package github.luckygc.am.infrastructure.runtime;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import github.luckygc.am.common.runtime.DeploymentTopology;
import github.luckygc.am.common.runtime.RuntimeCapabilityConfigurationException;
import github.luckygc.am.common.runtime.RuntimeDatabaseAdapter;
import github.luckygc.am.common.runtime.RuntimeLockAdapter;
import github.luckygc.am.common.runtime.RuntimeQueueAdapter;
import github.luckygc.am.common.storage.StorageType;
import github.luckygc.am.infrastructure.storage.FileStorageProperties;

class RuntimeCapabilityValidator implements InitializingBean {

    private static final String LOCAL_ADAPTER = "local";

    private static final String POSTGRESQL_DATABASE_ADAPTER = "postgresql";

    private static final String JDBC_JOB_STORE_TYPE = "jdbc";

    private static final List<String> CLUSTER_SAFE_CACHE_TYPES =
            List.of("redis", "hazelcast", "infinispan", "couchbase");

    private static final List<String> LOCAL_OR_DISABLED_CACHE_TYPES =
            List.of("none", "simple", "caffeine", "cache2k");

    private final RuntimeCapabilityProperties properties;

    private final List<RuntimeDatabaseAdapter> databaseAdapters;

    private final List<RuntimeQueueAdapter> queueAdapters;

    private final List<RuntimeLockAdapter> lockAdapters;

    private final FileStorageProperties fileStorageProperties;

    private final Environment environment;

    private final DataSource dataSource;

    RuntimeCapabilityValidator(
            RuntimeCapabilityProperties properties,
            List<RuntimeDatabaseAdapter> databaseAdapters,
            List<RuntimeQueueAdapter> queueAdapters,
            List<RuntimeLockAdapter> lockAdapters,
            FileStorageProperties fileStorageProperties,
            Environment environment,
            DataSource dataSource) {
        this.properties = properties;
        this.databaseAdapters = List.copyOf(databaseAdapters);
        this.queueAdapters = List.copyOf(queueAdapters);
        this.lockAdapters = List.copyOf(lockAdapters);
        this.fileStorageProperties = fileStorageProperties;
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        validateTopology();
        validateDatabase();
        validateQueue();
        validateLock();
        validateCache();
        validateScheduler();
        validateStorage();
    }

    private void validateTopology() {
        if (properties.getTopology() == null) {
            throw new RuntimeCapabilityConfigurationException(
                    "部署拓扑不能为空", "将 archive.runtime.topology 设置为 single 或 cluster");
        }
        requireText(properties.getNodeId(), "archive.runtime.node-id", "节点 ID");
    }

    private void validateDatabase() {
        String adapter = normalize(properties.getDatabase().getAdapter());
        requireText(adapter, "archive.runtime.database.adapter", "数据库 adapter");
        if (databaseAdapters.stream().noneMatch(candidate -> candidate.adapter().equals(adapter))) {
            throw new RuntimeCapabilityConfigurationException(
                    "数据库 adapter 未注册: " + adapter,
                    "提供对应 RuntimeDatabaseAdapter Bean，或修正 archive.runtime.database.adapter");
        }
        if (!POSTGRESQL_DATABASE_ADAPTER.equals(adapter)) {
            return;
        }
        String productName = databaseProductName();
        if (!productName.toLowerCase().contains("postgresql")) {
            throw new RuntimeCapabilityConfigurationException(
                    "数据库 adapter 配置为 postgresql，但实际数据库为: " + productName,
                    "修正 archive.runtime.database.adapter，或更换为 PostgreSQL 数据库");
        }
    }

    private String databaseProductName() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException exception) {
            throw new RuntimeCapabilityConfigurationException(
                    "无法检测数据库 adapter", "检查 spring.datasource 配置和数据库连通性", exception);
        }
    }

    private void validateQueue() {
        String adapter =
                resolveRegisteredAdapter(
                        normalize(properties.getQueue().getAdapter()),
                        normalize(properties.getQueue().getFallbackAdapter()),
                        queueAdapters.stream().map(RuntimeQueueAdapter::adapter).toList(),
                        "队列",
                        "archive.runtime.queue.adapter");
        requireText(adapter, "archive.runtime.queue.adapter", "队列 adapter");
        if (properties.getQueue().getMaxAttempts() < 1) {
            throw new RuntimeCapabilityConfigurationException(
                    "队列最大尝试次数必须大于 0", "将 archive.runtime.queue.max-attempts 设置为 1 或更大的整数");
        }
    }

    private void validateLock() {
        String adapter =
                resolveRegisteredAdapter(
                        normalize(properties.getLock().getAdapter()),
                        normalize(properties.getLock().getFallbackAdapter()),
                        lockAdapters.stream().map(RuntimeLockAdapter::adapter).toList(),
                        "分布式锁",
                        "archive.runtime.lock.adapter");
        requireText(adapter, "archive.runtime.lock.adapter", "分布式锁 adapter");
        if (isCluster() && LOCAL_ADAPTER.equals(adapter)) {
            throw new RuntimeCapabilityConfigurationException(
                    "集群部署不能使用本地锁 adapter",
                    "将 archive.runtime.lock.adapter 改为 database、redis 等集群安全 adapter");
        }
    }

    private void validateCache() {
        if (!isCluster()) {
            return;
        }
        String cacheType = normalize(environment.getProperty("spring.cache.type"));
        if (StringUtils.isBlank(cacheType)) {
            cacheType = "simple";
        }
        if (CLUSTER_SAFE_CACHE_TYPES.contains(cacheType)) {
            return;
        }
        if (properties.getCache().isShared()) {
            return;
        }
        if (LOCAL_OR_DISABLED_CACHE_TYPES.contains(cacheType)) {
            throw new RuntimeCapabilityConfigurationException(
                    "集群部署不能使用非共享 Spring Cache 实现: " + cacheType,
                    "将 spring.cache.type 设置为 redis、hazelcast、infinispan、couchbase，或接入共享 CacheManager");
        }
        throw new RuntimeCapabilityConfigurationException(
                "集群部署无法确认 Spring Cache 是否为共享实现: " + cacheType,
                "确认 CacheManager 由共享介质承载后设置 archive.runtime.cache.shared=true");
    }

    private void validateRelationsExistInCurrentSchema(
            List<String> relationNames,
            String missingMessagePrefix,
            String failureMessage,
            String action) {
        List<String> missingRelationNames = new ArrayList<>();
        for (String relationName : relationNames) {
            if (!relationExistsInCurrentSchema(relationName, failureMessage, action)) {
                missingRelationNames.add(relationName);
            }
        }
        if (!missingRelationNames.isEmpty()) {
            throw new RuntimeCapabilityConfigurationException(
                    missingMessagePrefix + String.join(", ", missingRelationNames), action);
        }
    }

    private boolean relationExistsInCurrentSchema(
            String relationName, String failureMessage, String action) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select exists ("
                                        + "select 1 from pg_class c "
                                        + "join pg_namespace n on n.oid = c.relnamespace "
                                        + "where n.nspname = current_schema() "
                                        + "and c.relname in (?, ?)"
                                        + ")")) {
            statement.setString(1, relationName);
            statement.setString(2, relationName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        } catch (SQLException exception) {
            throw new RuntimeCapabilityConfigurationException(failureMessage, action, exception);
        }
    }

    private void validateScheduler() {
        boolean quartzAutoStartup =
                environment.getProperty("spring.quartz.auto-startup", Boolean.class, true);
        if (!quartzAutoStartup) {
            return;
        }
        String jobStoreType = normalize(environment.getProperty("spring.quartz.job-store-type"));
        if (StringUtils.isBlank(jobStoreType)) {
            jobStoreType = "memory";
        }
        if (!JDBC_JOB_STORE_TYPE.equals(jobStoreType)) {
            validateClusterWithoutQuartzJdbc(jobStoreType);
            return;
        }
        validateQuartzJdbcTables();
        if (!isCluster()) {
            return;
        }
        boolean quartzClustered =
                environment.getProperty(
                        "spring.quartz.properties.org.quartz.jobStore.isClustered",
                        Boolean.class,
                        false);
        if (!quartzClustered) {
            throw new RuntimeCapabilityConfigurationException(
                    "集群部署启用 Quartz JDBC 调度时必须启用 Quartz 集群配置",
                    "设置 spring.quartz.properties.org.quartz.jobStore.isClustered=true");
        }
    }

    private void validateClusterWithoutQuartzJdbc(String jobStoreType) {
        if (!isCluster()) {
            return;
        }
        throw new RuntimeCapabilityConfigurationException(
                "集群部署启用 Quartz 调度时不能使用非 JDBC JobStore: " + jobStoreType,
                "将 spring.quartz.job-store-type 设置为 jdbc，或设置 spring.quartz.auto-startup=false 关闭调度");
    }

    private void validateQuartzJdbcTables() {
        String tablePrefix =
                normalize(
                        environment.getProperty(
                                "spring.quartz.properties.org.quartz.jobStore.tablePrefix",
                                "QRTZ_"));
        requireText(
                tablePrefix,
                "spring.quartz.properties.org.quartz.jobStore.tablePrefix",
                "Quartz 表前缀");
        validateRelationsExistInCurrentSchema(
                quartzJdbcTableNames(tablePrefix),
                "Quartz JDBC JobStore 启用但 Quartz 表不存在: ",
                "无法检测 Quartz JDBC 表",
                "确认 Flyway 已迁移 Quartz JDBC 表，或修正 spring.quartz.properties.org.quartz.jobStore.tablePrefix");
    }

    private List<String> quartzJdbcTableNames(String tablePrefix) {
        return List.of(
                tablePrefix + "LOCKS",
                tablePrefix + "JOB_DETAILS",
                tablePrefix + "TRIGGERS",
                tablePrefix + "SIMPLE_TRIGGERS",
                tablePrefix + "CRON_TRIGGERS",
                tablePrefix + "SIMPROP_TRIGGERS",
                tablePrefix + "BLOB_TRIGGERS",
                tablePrefix + "CALENDARS",
                tablePrefix + "PAUSED_TRIGGER_GRPS",
                tablePrefix + "FIRED_TRIGGERS",
                tablePrefix + "SCHEDULER_STATE");
    }

    private void validateStorage() {
        if (!isCluster()) {
            return;
        }
        StorageType storageAdapter = storageAdapter();
        if (storageAdapter != StorageType.local) {
            if (objectStorageConfigured()) {
                return;
            }
            throw new RuntimeCapabilityConfigurationException(
                    "集群部署选择对象存储 adapter 但对象存储配置不完整: " + storageAdapter,
                    "补齐 archive.storage.object.bucket、region、access-key、secret-key，或将 "
                            + "archive.storage.adapter 改为 local 并确认共享挂载");
        }
        if (properties.getStorage().isLocalShared()) {
            return;
        }
        throw new RuntimeCapabilityConfigurationException(
                "集群部署不能使用非共享本地文件存储",
                "将 archive.storage.adapter 改为 s3、minio、cos、oss、obs，或确认本地存储为共享挂载后设置 "
                        + "archive.runtime.storage.local-shared=true");
    }

    private StorageType storageAdapter() {
        String adapter = normalize(fileStorageProperties.getAdapter());
        try {
            return StorageType.fromValue(adapter);
        } catch (IllegalArgumentException exception) {
            throw new RuntimeCapabilityConfigurationException(
                    "文件存储 adapter 不支持: " + adapter,
                    "将 archive.storage.adapter 设置为 local、s3、minio、cos、oss 或 obs");
        }
    }

    private boolean objectStorageConfigured() {
        FileStorageProperties.ObjectStorage object = fileStorageProperties.getObject();
        return StringUtils.isNoneBlank(
                object.getBucket(),
                object.getRegion(),
                object.getAccessKey(),
                object.getSecretKey());
    }

    private boolean isCluster() {
        return properties.getTopology() == DeploymentTopology.cluster;
    }

    private String normalize(String value) {
        return StringUtils.defaultString(value).trim();
    }

    private String resolveRegisteredAdapter(
            String adapter,
            String fallbackAdapter,
            Iterable<String> registeredAdapters,
            String capability,
            String propertyName) {
        requireText(adapter, propertyName, capability + " adapter");
        if (contains(registeredAdapters, adapter)) {
            return adapter;
        }
        if (StringUtils.isNotBlank(fallbackAdapter)
                && contains(registeredAdapters, fallbackAdapter)) {
            return fallbackAdapter;
        }
        throw new RuntimeCapabilityConfigurationException(
                capability + " adapter 未注册: " + adapter,
                "提供对应 adapter Bean，或修正 "
                        + propertyName
                        + "；允许降级时配置 "
                        + propertyName.replace(".adapter", ".fallback-adapter"));
    }

    private boolean contains(Iterable<String> values, String target) {
        for (String value : values) {
            if (value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private void requireText(String adapter, String propertyName, String capability) {
        if (StringUtils.isBlank(adapter)) {
            throw new RuntimeCapabilityConfigurationException(
                    capability + "不能为空", "配置 " + propertyName);
        }
    }
}
