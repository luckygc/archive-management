package github.luckygc.am.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import github.luckygc.am.common.runtime.RuntimeCapabilityConfigurationException;
import github.luckygc.am.common.runtime.RuntimeDatabase;
import github.luckygc.am.common.runtime.RuntimeLockManager;
import github.luckygc.am.common.runtime.RuntimeQueue;
import github.luckygc.am.infrastructure.storage.FileStorageProperties;

@DisplayName("运行时能力 Spring Context")
class RuntimeCapabilityContextTests {

    @Test
    @DisplayName("默认单机数据库运行时可以启动")
    void defaultSingleNodeDatabaseRuntimeStarts() {
        contextRunner()
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(RuntimeDatabase.class).adapter())
                                    .isEqualTo("postgresql");
                            assertThat(context.getBean(RuntimeQueue.class))
                                    .isInstanceOf(DatabaseRuntimeQueue.class);
                            assertThat(context.getBean(RuntimeLockManager.class))
                                    .isInstanceOf(DatabaseRuntimeLockManager.class);
                        });
    }

    @Test
    @DisplayName("单机部署允许使用本地锁")
    void singleNodeCanUseLocalLock() {
        contextRunner()
                .withPropertyValues("archive.runtime.lock.adapter=local")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(RuntimeLockManager.class))
                                    .isInstanceOf(LocalRuntimeLockManager.class);
                        });
    }

    @Test
    @DisplayName("集群部署设置共享能力后可以启动")
    void clusterCanUseDatabaseRuntimeWhenClusterFlagsAreSet() {
        contextRunner()
                .withPropertyValues(
                        "archive.runtime.topology=cluster",
                        "spring.cache.type=redis",
                        "spring.quartz.job-store-type=jdbc",
                        "spring.quartz.properties.org.quartz.jobStore.isClustered=true",
                        "archive.runtime.storage.local-shared=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("队列 adapter 缺失时启动失败")
    void missingQueueAdapterFailsAtStartup() {
        contextRunner()
                .withPropertyValues(
                        "archive.runtime.queue.adapter=rabbitmq",
                        "archive.runtime.queue.fallback-adapter=")
                .run(
                        context ->
                                assertThat(context.getStartupFailure())
                                        .hasRootCauseInstanceOf(
                                                RuntimeCapabilityConfigurationException.class)
                                        .hasStackTraceContaining("队列 adapter 未注册: rabbitmq"));
    }

    @Test
    @DisplayName("队列 adapter 未接入时默认降级到数据库队列")
    void missingQueueAdapterFallsBackToDatabaseAtStartup() {
        contextRunner()
                .withPropertyValues("archive.runtime.queue.adapter=nats")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(RuntimeQueue.class))
                                    .isInstanceOf(DatabaseRuntimeQueue.class);
                        });
    }

    @Test
    @DisplayName("Quartz JDBC 表缺失时启动失败")
    void quartzJdbcRejectsMissingQuartzTableAtStartup() {
        contextRunner("PostgreSQL", false)
                .run(
                        context ->
                                assertThat(context.getStartupFailure())
                                        .hasRootCauseInstanceOf(
                                                RuntimeCapabilityConfigurationException.class)
                                        .hasStackTraceContaining("Quartz 表不存在"));
    }

    @Test
    @DisplayName("关闭 Quartz 自动启动后不校验 Quartz 表")
    void quartzValidationIsSkippedWhenAutoStartupIsDisabled() {
        contextRunner()
                .withPropertyValues("spring.quartz.auto-startup=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("PostgreSQL adapter 遇到非 PostgreSQL 数据库时启动失败")
    void postgreSqlAdapterRejectsDifferentDatabaseAtStartup() {
        contextRunner("H2")
                .run(
                        context ->
                                assertThat(context.getStartupFailure())
                                        .hasRootCauseInstanceOf(
                                                RuntimeCapabilityConfigurationException.class)
                                        .hasStackTraceContaining("实际数据库为: H2"));
    }

    private ApplicationContextRunner contextRunner() {
        return contextRunner("PostgreSQL");
    }

    private ApplicationContextRunner contextRunner(String databaseProductName) {
        return contextRunner(databaseProductName, true);
    }

    private ApplicationContextRunner contextRunner(
            String databaseProductName, boolean... relationExistsResults) {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        RuntimeCapabilityConfiguration.class, RuntimePropertiesConfig.class)
                .withBean(RuntimeMapper.class, () -> mock(RuntimeMapper.class))
                .withBean(
                        DataSource.class,
                        () -> dataSource(databaseProductName, relationExistsResults))
                .withPropertyValues(
                        "spring.cache.type=none",
                        "spring.quartz.job-store-type=jdbc",
                        "spring.quartz.properties.org.quartz.jobStore.isClustered=true");
    }

    private static DataSource dataSource(String productName, boolean... relationExistsResults) {
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({RuntimeCapabilityProperties.class, FileStorageProperties.class})
    static class RuntimePropertiesConfig {}
}
