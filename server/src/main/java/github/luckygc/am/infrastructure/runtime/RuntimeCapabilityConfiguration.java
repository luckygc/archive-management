package github.luckygc.am.infrastructure.runtime;

import java.util.List;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import github.luckygc.am.common.runtime.RuntimeCapabilityConfigurationException;
import github.luckygc.am.common.runtime.RuntimeDatabase;
import github.luckygc.am.common.runtime.RuntimeDatabaseAdapter;
import github.luckygc.am.common.runtime.RuntimeLockAdapter;
import github.luckygc.am.common.runtime.RuntimeLockManager;
import github.luckygc.am.common.runtime.RuntimeQueue;
import github.luckygc.am.common.runtime.RuntimeQueueAdapter;
import github.luckygc.am.infrastructure.storage.FileStorageProperties;

@Configuration
class RuntimeCapabilityConfiguration {

    @Bean
    RuntimeDatabaseAdapter postgreSqlRuntimeDatabaseAdapter() {
        return new PostgreSqlRuntimeDatabaseAdapter();
    }

    @Bean
    RuntimeQueueAdapter databaseRuntimeQueueAdapter(
            RuntimeMapper runtimeMapper, RuntimeCapabilityProperties properties) {
        return new DatabaseRuntimeQueue(runtimeMapper, properties.getQueue().getMaxAttempts());
    }

    @Bean
    RuntimeLockAdapter databaseRuntimeLockAdapter(RuntimeMapper runtimeMapper) {
        return new DatabaseRuntimeLockManager(runtimeMapper);
    }

    @Bean
    RuntimeLockAdapter localRuntimeLockAdapter() {
        return new LocalRuntimeLockManager();
    }

    @Bean
    @Primary
    RuntimeDatabase runtimeDatabase(
            RuntimeCapabilityProperties properties, List<RuntimeDatabaseAdapter> adapters) {
        return findAdapter(
                adapters,
                properties.getDatabase().getAdapter(),
                "archive.runtime.database.adapter",
                "数据库");
    }

    @Bean
    @Primary
    RuntimeQueue runtimeQueue(
            RuntimeCapabilityProperties properties, List<RuntimeQueueAdapter> adapters) {
        return findAdapter(
                adapters,
                properties.getQueue().getAdapter(),
                properties.getQueue().getFallbackAdapter(),
                "archive.runtime.queue.adapter",
                "队列");
    }

    @Bean
    @Primary
    RuntimeLockManager runtimeLockManager(
            RuntimeCapabilityProperties properties, List<RuntimeLockAdapter> adapters) {
        return findAdapter(
                adapters,
                properties.getLock().getAdapter(),
                properties.getLock().getFallbackAdapter(),
                "archive.runtime.lock.adapter",
                "分布式锁");
    }

    @Bean
    RuntimeCapabilityValidator runtimeCapabilityValidator(
            RuntimeCapabilityProperties properties,
            List<RuntimeDatabaseAdapter> databaseAdapters,
            List<RuntimeQueueAdapter> queueAdapters,
            List<RuntimeLockAdapter> lockAdapters,
            FileStorageProperties fileStorageProperties,
            Environment environment,
            DataSource dataSource) {
        return new RuntimeCapabilityValidator(
                properties,
                databaseAdapters,
                queueAdapters,
                lockAdapters,
                fileStorageProperties,
                environment,
                dataSource);
    }

    private <T> T findAdapter(
            List<? extends T> adapters, String adapter, String propertyName, String capability) {
        return findAdapter(adapters, adapter, "", propertyName, capability);
    }

    private <T> T findAdapter(
            List<? extends T> adapters,
            String adapter,
            String fallbackAdapter,
            String propertyName,
            String capability) {
        String normalized = StringUtils.defaultString(adapter).trim();
        if (StringUtils.isBlank(normalized)) {
            throw new RuntimeCapabilityConfigurationException(
                    capability + " adapter 不能为空", "配置 " + propertyName);
        }
        java.util.Optional<T> selected = findRegisteredAdapter(adapters, normalized);
        if (selected.isPresent()) {
            return selected.orElseThrow();
        }
        selected =
                findRegisteredAdapter(adapters, StringUtils.defaultString(fallbackAdapter).trim());
        if (selected.isPresent()) {
            return selected.orElseThrow();
        }
        throw new RuntimeCapabilityConfigurationException(
                capability + " adapter 未注册: " + normalized,
                "提供对应 adapter Bean，或修正 "
                        + propertyName
                        + "；允许降级时配置 "
                        + fallbackPropertyName(propertyName));
    }

    private <T> java.util.Optional<T> findRegisteredAdapter(
            List<? extends T> adapters, String adapter) {
        if (StringUtils.isBlank(adapter)) {
            return java.util.Optional.empty();
        }
        return adapters.stream()
                .filter(candidate -> adapterName(candidate).equals(adapter))
                .findFirst()
                .map(candidate -> (T) candidate);
    }

    private String fallbackPropertyName(String propertyName) {
        return propertyName.replace(".adapter", ".fallback-adapter");
    }

    private String adapterName(Object adapter) {
        if (adapter instanceof RuntimeDatabaseAdapter runtimeDatabaseAdapter) {
            return runtimeDatabaseAdapter.adapter();
        }
        if (adapter instanceof RuntimeQueueAdapter runtimeQueueAdapter) {
            return runtimeQueueAdapter.adapter();
        }
        if (adapter instanceof RuntimeLockAdapter runtimeLockAdapter) {
            return runtimeLockAdapter.adapter();
        }
        throw new IllegalArgumentException("未知 adapter 类型: " + adapter.getClass().getName());
    }
}
