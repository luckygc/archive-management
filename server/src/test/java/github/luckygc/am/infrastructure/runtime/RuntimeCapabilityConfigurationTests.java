package github.luckygc.am.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.runtime.RuntimeCapabilityConfigurationException;
import github.luckygc.am.common.runtime.RuntimeDatabase;
import github.luckygc.am.common.runtime.RuntimeLockManager;
import github.luckygc.am.common.runtime.RuntimeQueue;

@DisplayName("运行时能力 Bean 选择")
class RuntimeCapabilityConfigurationTests {

    private final RuntimeCapabilityConfiguration configuration =
            new RuntimeCapabilityConfiguration();

    @Test
    @DisplayName("按配置选择队列 adapter")
    void selectConfiguredQueueAdapter() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        RuntimeQueueAdapterStub queueAdapter = new RuntimeQueueAdapterStub();

        RuntimeQueue runtimeQueue = configuration.runtimeQueue(properties, List.of(queueAdapter));

        assertThat(runtimeQueue).isSameAs(queueAdapter);
    }

    @Test
    @DisplayName("按配置选择数据库 adapter")
    void selectConfiguredDatabaseAdapter() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();

        RuntimeDatabase database =
                configuration.runtimeDatabase(
                        properties, List.of(new PostgreSqlRuntimeDatabaseAdapter()));

        assertThat(database.adapter()).isEqualTo("postgresql");
    }

    @Test
    @DisplayName("配置的队列 adapter 缺失时抛出配置异常")
    void rejectMissingConfiguredQueueAdapter() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.getQueue().setAdapter("rabbitmq");
        properties.getQueue().setFallbackAdapter("");

        assertThatThrownBy(() -> configuration.runtimeQueue(properties, List.of()))
                .isInstanceOf(RuntimeCapabilityConfigurationException.class)
                .hasMessageContaining("队列 adapter 未注册: rabbitmq");
    }

    @Test
    @DisplayName("队列 adapter 未接入时可以降级到 database")
    void fallbackMissingQueueAdapterToDatabase() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.getQueue().setAdapter("nats");
        RuntimeQueueAdapterStub queueAdapter = new RuntimeQueueAdapterStub();

        RuntimeQueue runtimeQueue = configuration.runtimeQueue(properties, List.of(queueAdapter));

        assertThat(runtimeQueue).isSameAs(queueAdapter);
    }

    @Test
    @DisplayName("锁 adapter 未接入时可以降级到 database")
    void fallbackMissingLockAdapterToDatabase() {
        RuntimeCapabilityProperties properties = new RuntimeCapabilityProperties();
        properties.getLock().setAdapter("redis");
        RuntimeMapper runtimeMapper = mock(RuntimeMapper.class);
        DatabaseRuntimeLockManager databaseLockManager =
                new DatabaseRuntimeLockManager(runtimeMapper);

        RuntimeLockManager lockManager =
                configuration.runtimeLockManager(
                        properties, List.of(databaseLockManager, new LocalRuntimeLockManager()));

        assertThat(lockManager).isSameAs(databaseLockManager);
    }

    @Test
    @DisplayName("内置 adapter 暴露稳定名称")
    void builtInAdaptersExposeStableNames() {
        RuntimeMapper runtimeMapper = mock(RuntimeMapper.class);

        assertThat(new DatabaseRuntimeQueue(runtimeMapper).adapter()).isEqualTo("database");
        assertThat(new PostgreSqlRuntimeDatabaseAdapter().adapter()).isEqualTo("postgresql");
        assertThat(new DatabaseRuntimeLockManager(runtimeMapper).adapter()).isEqualTo("database");
        assertThat(new LocalRuntimeLockManager().adapter()).isEqualTo("local");
    }

    private static class RuntimeQueueAdapterStub extends DatabaseRuntimeQueue {

        RuntimeQueueAdapterStub() {
            super(mock(RuntimeMapper.class));
        }
    }
}
