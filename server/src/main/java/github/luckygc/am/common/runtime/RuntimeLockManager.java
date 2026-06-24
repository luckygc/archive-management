package github.luckygc.am.common.runtime;

import java.time.Duration;
import java.util.Optional;

/** 提供跨任务或跨节点互斥的运行时锁能力。 */
public interface RuntimeLockManager {

    /** 尝试获取指定锁，返回空表示当前锁已被其他持有者占用。 */
    Optional<RuntimeLockLease> tryLock(String lockName, String ownerId, Duration leaseDuration);

    /** 释放租约；实现必须保证重复释放不会破坏其他持有者的锁。 */
    void unlock(RuntimeLockLease lease);
}
