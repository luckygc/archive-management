package github.luckygc.am.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import github.luckygc.am.common.runtime.DeploymentTopology;
import github.luckygc.am.common.runtime.RuntimeCapabilityConfigurationException;
import github.luckygc.am.infrastructure.storage.FileStorageProperties;

@DisplayName("运行时能力启动校验")
class RuntimeCapabilityValidatorTests {

    @Test
    @DisplayName("默认单机配置通过校验")
    void defaultSingleNodeConfigurationIsValid() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties(), env());

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("队列 adapter 未注册时拒绝启动")
    void rejectMissingQueueAdapter() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.getQueue().setAdapter("rabbitmq");
        properties.getQueue().setFallbackAdapter("");
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties(), env());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("队列 adapter 未注册: rabbitmq")
                .satisfies(
                        exception ->
                                assertThat(action(exception))
                                        .contains("archive.runtime.queue.adapter"));
    }

    @Test
    @DisplayName("队列 adapter 未接入时可以降级到 database")
    void fallbackMissingQueueAdapterToDatabase() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.getQueue().setAdapter("nats");
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties(), env());

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("队列最大尝试次数小于 1 时拒绝启动")
    void rejectInvalidQueueMaxAttempts() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.getQueue().setMaxAttempts(0);
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties(), env());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("队列最大尝试次数必须大于 0")
                .satisfies(
                        exception ->
                                assertThat(action(exception))
                                        .contains("archive.runtime.queue.max-attempts"));
    }

    @Test
    @DisplayName("锁 adapter 未接入时可以降级到 database")
    void fallbackMissingLockAdapterToDatabase() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.getLock().setAdapter("redis");
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties(), env());

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Quartz 自动启动关闭时不校验 Quartz 表")
    void skipQuartzValidationWhenAutoStartupIsDisabled() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        RuntimeCapabilityValidator validator =
                validator(
                        properties,
                        fileStorageProperties(),
                        envWithQuartzAutoStartup(false),
                        dataSource("PostgreSQL", false));

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Quartz JDBC 表不存在时拒绝启动")
    void rejectQuartzJdbcWhenQuartzTableIsMissing() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        RuntimeCapabilityValidator validator =
                validator(
                        properties,
                        fileStorageProperties(),
                        env(),
                        dataSource("PostgreSQL", false));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("Quartz 表不存在: QRTZ_LOCKS")
                .satisfies(
                        exception ->
                                assertThat(action(exception))
                                        .contains("org.quartz.jobStore.tablePrefix"));
    }

    @Test
    @DisplayName("数据库 adapter 未注册时拒绝启动")
    void rejectMissingDatabaseAdapter() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.getDatabase().setAdapter("mysql");
        RuntimeCapabilityValidator validator =
                new RuntimeCapabilityValidator(
                        properties,
                        List.of(),
                        List.of(new DatabaseRuntimeQueue(mock(RuntimeMapper.class))),
                        List.of(new DatabaseRuntimeLockManager(mock(RuntimeMapper.class))),
                        fileStorageProperties(),
                        env(),
                        dataSource("PostgreSQL"));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("数据库 adapter 未注册: mysql");
    }

    @Test
    @DisplayName("PostgreSQL adapter 与实际数据库不匹配时拒绝启动")
    void rejectPostgreSqlAdapterWhenActualDatabaseIsDifferent() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties(), env(), dataSource("H2"));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("实际数据库为: H2");
    }

    @Test
    @DisplayName("节点 ID 为空时拒绝启动")
    void rejectMissingNodeId() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.setNodeId(" ");
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties(), env());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("节点 ID不能为空")
                .satisfies(
                        exception ->
                                assertThat(action(exception)).contains("archive.runtime.node-id"));
    }

    @Test
    @DisplayName("集群部署不能使用本地锁")
    void rejectClusterWithLocalLock() {
        RuntimeCapabilityProperties properties = clusterProperties();
        properties.getLock().setAdapter("local");
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties(), clusterEnv());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("集群部署不能使用本地锁 adapter");
    }

    @Test
    @DisplayName("集群部署不能使用非共享本地文件存储")
    void rejectClusterWithoutSharedStorage() {
        RuntimeCapabilityProperties properties = clusterProperties();
        properties.getStorage().setLocalShared(false);
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties(), clusterEnv());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("集群部署不能使用非共享本地文件存储");
    }

    @Test
    @DisplayName("集群部署配置对象存储时允许启动")
    void allowClusterWhenObjectStorageIsConfigured() {
        RuntimeCapabilityProperties properties = clusterProperties();
        properties.getStorage().setLocalShared(false);
        RuntimeCapabilityValidator validator =
                validator(properties, objectStorageProperties(), clusterEnv());

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("集群部署不能使用无共享 Spring Cache")
    void rejectClusterWithNoOpCache() {
        RuntimeCapabilityProperties properties = clusterProperties();
        RuntimeCapabilityValidator validator =
                validator(
                        properties,
                        objectStorageProperties(),
                        clusterEnv().withProperty("spring.cache.type", "none"));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("集群部署不能使用非共享 Spring Cache 实现: none");
    }

    @Test
    @DisplayName("集群部署 generic CacheManager 需要声明共享介质")
    void rejectClusterWithUnconfirmedGenericCache() {
        RuntimeCapabilityProperties properties = clusterProperties();
        RuntimeCapabilityValidator validator =
                validator(
                        properties,
                        objectStorageProperties(),
                        clusterEnv().withProperty("spring.cache.type", "generic"));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("无法确认 Spring Cache 是否为共享实现: generic");
    }

    @Test
    @DisplayName("集群部署 generic CacheManager 声明共享介质后允许启动")
    void allowClusterWithSharedGenericCache() {
        RuntimeCapabilityProperties properties = clusterProperties();
        properties.getCache().setShared(true);
        RuntimeCapabilityValidator validator =
                validator(
                        properties,
                        objectStorageProperties(),
                        clusterEnv().withProperty("spring.cache.type", "generic"));

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("对象存储已配置但默认仍为本地存储时按本地存储校验")
    void rejectClusterWhenObjectStorageIsConfiguredButDefaultAdapterIsLocal() {
        RuntimeCapabilityProperties properties = clusterProperties();
        properties.getStorage().setLocalShared(false);
        FileStorageProperties fileStorageProperties = objectStorageProperties();
        fileStorageProperties.setAdapter("local");
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties, clusterEnv());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("集群部署不能使用非共享本地文件存储");
    }

    @Test
    @DisplayName("不支持的文件存储 adapter 会被拒绝")
    void rejectUnsupportedStorageAdapter() {
        RuntimeCapabilityProperties properties = clusterProperties();
        FileStorageProperties fileStorageProperties = fileStorageProperties();
        fileStorageProperties.setAdapter("ftp");
        RuntimeCapabilityValidator validator =
                validator(properties, fileStorageProperties, clusterEnv());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("文件存储 adapter 不支持: ftp");
    }

    @Test
    @DisplayName("集群 Quartz 未启用集群属性时拒绝启动")
    void rejectClusterQuartzWithoutClusteredQuartzProperty() {
        RuntimeCapabilityProperties properties = clusterProperties();
        RuntimeCapabilityValidator validator =
                validator(
                        properties,
                        objectStorageProperties(),
                        clusterEnv()
                                .withProperty("spring.quartz.job-store-type", "jdbc")
                                .withProperty(
                                        "spring.quartz.properties.org.quartz.jobStore.isClustered",
                                        "false"));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("必须启用 Quartz 集群配置");
    }

    @Test
    @DisplayName("集群 Quartz 不能使用内存 JobStore")
    void rejectClusterQuartzWithMemoryJobStore() {
        RuntimeCapabilityProperties properties = clusterProperties();
        RuntimeCapabilityValidator validator =
                validator(
                        properties,
                        objectStorageProperties(),
                        clusterEnv().withProperty("spring.quartz.job-store-type", "memory"));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("不能使用非 JDBC JobStore");
    }

    private RuntimeCapabilityValidator validator(
            RuntimeCapabilityProperties properties,
            FileStorageProperties fileStorageProperties,
            MockEnvironment environment) {
        return validator(properties, fileStorageProperties, environment, dataSource("PostgreSQL"));
    }

    private RuntimeCapabilityValidator validator(
            RuntimeCapabilityProperties properties,
            FileStorageProperties fileStorageProperties,
            MockEnvironment environment,
            DataSource dataSource) {
        RuntimeMapper runtimeMapper = mock(RuntimeMapper.class);
        return new RuntimeCapabilityValidator(
                properties,
                List.of(new PostgreSqlRuntimeDatabaseAdapter()),
                List.of(new DatabaseRuntimeQueue(runtimeMapper)),
                List.of(
                        new DatabaseRuntimeLockManager(runtimeMapper),
                        new LocalRuntimeLockManager()),
                fileStorageProperties,
                environment,
                dataSource);
    }

    private DataSource dataSource(String productName) {
        return dataSource(productName, true);
    }

    private DataSource dataSource(String productName, boolean... relationExistsResults) {
        try {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            DatabaseMetaData metaData = mock(DatabaseMetaData.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);
            AtomicInteger relationExistsIndex = new AtomicInteger();
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getDatabaseProductName()).thenReturn(productName);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getBoolean(1))
                    .thenAnswer(
                            ignored -> {
                                int index = relationExistsIndex.getAndIncrement();
                                int resultIndex = Math.min(index, relationExistsResults.length - 1);
                                return relationExistsResults[resultIndex];
                            });
            return dataSource;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private RuntimeCapabilityProperties clusterProperties() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.setTopology(DeploymentTopology.cluster);
        properties.getStorage().setLocalShared(true);
        return properties;
    }

    private MockEnvironment env() {
        return new MockEnvironment()
                .withProperty("spring.cache.type", "none")
                .withProperty("spring.quartz.job-store-type", "jdbc")
                .withProperty("spring.quartz.properties.org.quartz.jobStore.isClustered", "true");
    }

    private MockEnvironment clusterEnv() {
        return env().withProperty("spring.cache.type", "redis");
    }

    private MockEnvironment envWithQuartzAutoStartup(boolean autoStartup) {
        return env().withProperty("spring.quartz.auto-startup", Boolean.toString(autoStartup));
    }

    private FileStorageProperties fileStorageProperties() {
        return new FileStorageProperties();
    }

    private FileStorageProperties objectStorageProperties() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setAdapter("s3");
        properties.getObject().setBucket("archive");
        properties.getObject().setRegion("us-east-1");
        properties.getObject().setAccessKey("access-key");
        properties.getObject().setSecretKey("secret-key");
        return properties;
    }

    private static String action(Throwable exception) {
        return ((RuntimeCapabilityConfigurationException) exception).action();
    }
}
