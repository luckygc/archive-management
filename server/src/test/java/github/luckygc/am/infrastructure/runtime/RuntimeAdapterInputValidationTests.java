package github.luckygc.am.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.runtime.RuntimeMessage;

@DisplayName("运行时 adapter 输入校验")
class RuntimeAdapterInputValidationTests {

    @Test
    @DisplayName("数据库队列拒绝空队列名且不访问 mapper")
    void databaseQueueRejectsBlankQueueNameBeforeMapperAccess() {
        RuntimeMapper runtimeMapper = mock(RuntimeMapper.class);
        DatabaseRuntimeQueue runtimeQueue = new DatabaseRuntimeQueue(runtimeMapper);

        assertThatThrownBy(() -> runtimeQueue.enqueue(" ", message(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("队列名称不能为空");
        assertThatThrownBy(() -> runtimeQueue.claim(" ", "node-a", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("队列名称不能为空");

        verifyNoInteractions(runtimeMapper);
    }

    @Test
    @DisplayName("数据库队列拒绝空 worker 和无效租约")
    void databaseQueueRejectsBlankWorkerAndInvalidLease() {
        RuntimeMapper runtimeMapper = mock(RuntimeMapper.class);
        DatabaseRuntimeQueue runtimeQueue = new DatabaseRuntimeQueue(runtimeMapper);

        assertThatThrownBy(() -> runtimeQueue.claim("jobs", " ", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worker ID不能为空");
        assertThatThrownBy(() -> runtimeQueue.claim("jobs", "node-a", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("队列租约必须至少 1 秒");

        verifyNoInteractions(runtimeMapper);
    }

    @Test
    @DisplayName("数据库队列拒绝无效任务 ID")
    void databaseQueueRejectsInvalidJobId() {
        RuntimeMapper runtimeMapper = mock(RuntimeMapper.class);
        DatabaseRuntimeQueue runtimeQueue = new DatabaseRuntimeQueue(runtimeMapper);

        assertThatThrownBy(() -> runtimeQueue.complete(0L, "node-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务 ID 必须大于 0");
        assertThatThrownBy(() -> runtimeQueue.fail(null, "node-a", "failed", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务 ID 必须大于 0");

        verifyNoInteractions(runtimeMapper);
    }

    @Test
    @DisplayName("数据库锁拒绝空名称、空持有者和无效租约")
    void databaseLockRejectsInvalidInputsBeforeMapperAccess() {
        RuntimeMapper runtimeMapper = mock(RuntimeMapper.class);
        DatabaseRuntimeLockManager lockManager = new DatabaseRuntimeLockManager(runtimeMapper);

        assertThatThrownBy(() -> lockManager.tryLock(" ", "node-a", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("锁名称不能为空");
        assertThatThrownBy(() -> lockManager.tryLock("rebuild-search", " ", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("锁持有者不能为空");
        assertThatThrownBy(() -> lockManager.tryLock("rebuild-search", "node-a", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("锁租约必须至少 1 秒");

        verifyNoInteractions(runtimeMapper);
    }

    @Test
    @DisplayName("本地锁拒绝空名称、空持有者和无效租约")
    void localLockRejectsInvalidInputs() {
        LocalRuntimeLockManager lockManager = new LocalRuntimeLockManager();

        assertThatThrownBy(() -> lockManager.tryLock(" ", "node-a", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("锁名称不能为空");
        assertThatThrownBy(() -> lockManager.tryLock("rebuild-search", " ", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("锁持有者不能为空");
        assertThatThrownBy(() -> lockManager.tryLock("rebuild-search", "node-a", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("锁租约必须至少 1 秒");
    }

    private RuntimeMessage message() {
        return new RuntimeMessage(
                "message-1",
                "/tests/runtime",
                "github.luckygc.am.test.runtime",
                "runtime-test",
                "{}",
                Instant.now());
    }
}
