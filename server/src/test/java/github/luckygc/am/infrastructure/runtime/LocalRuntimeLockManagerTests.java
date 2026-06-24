package github.luckygc.am.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("本地运行时锁")
class LocalRuntimeLockManagerTests {

    @Test
    @DisplayName("同一持有者可续租且其他持有者不能抢占")
    void allowSameOwnerToRenewLeaseAndRejectOtherOwner() {
        LocalRuntimeLockManager lockManager = new LocalRuntimeLockManager();

        var first = lockManager.tryLock("rebuild-search", "node-a", Duration.ofMinutes(1));
        var renewed = lockManager.tryLock("rebuild-search", "node-a", Duration.ofMinutes(1));
        var other = lockManager.tryLock("rebuild-search", "node-b", Duration.ofMinutes(1));

        assertThat(first).isPresent();
        assertThat(renewed).isPresent();
        assertThat(other).isEmpty();
    }

    @Test
    @DisplayName("unlock 释放租约")
    void unlockReleasesLease() {
        LocalRuntimeLockManager lockManager = new LocalRuntimeLockManager();

        var first = lockManager.tryLock("rebuild-search", "node-a", Duration.ofMinutes(1));
        lockManager.unlock(first.orElseThrow());

        assertThat(lockManager.tryLock("rebuild-search", "node-b", Duration.ofMinutes(1)))
                .isPresent();
    }

    @Test
    @DisplayName("租约过期后允许其他持有者接管")
    void allowOtherOwnerAfterLeaseExpired() throws InterruptedException {
        LocalRuntimeLockManager lockManager = new LocalRuntimeLockManager();

        var first = lockManager.tryLock("rebuild-search", "node-a", Duration.ofSeconds(1));

        assertThat(first).isPresent();
        assertThat(lockManager.tryLock("rebuild-search", "node-b", Duration.ofMinutes(1)))
                .isEmpty();

        Thread.sleep(1200L);

        assertThat(lockManager.tryLock("rebuild-search", "node-b", Duration.ofMinutes(1)))
                .isPresent();
    }

    @Test
    @DisplayName("close 多次调用只释放一次租约")
    void closeReleasesLeaseOnce() {
        LocalRuntimeLockManager lockManager = new LocalRuntimeLockManager();

        var first = lockManager.tryLock("rebuild-search", "node-a", Duration.ofMinutes(1));
        first.orElseThrow().close();
        first.orElseThrow().close();

        assertThat(lockManager.tryLock("rebuild-search", "node-b", Duration.ofMinutes(1)))
                .isPresent();
    }
}
