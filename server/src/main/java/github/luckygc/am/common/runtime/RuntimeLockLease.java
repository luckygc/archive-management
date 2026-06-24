package github.luckygc.am.common.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 运行时锁租约，关闭租约会释放对应锁。 */
public final class RuntimeLockLease implements AutoCloseable {

    private final String lockName;

    private final String ownerId;

    private final Instant lockedUntil;

    private final Consumer<RuntimeLockLease> releaser;

    private final AtomicBoolean closed = new AtomicBoolean();

    public RuntimeLockLease(String lockName, String ownerId, Instant lockedUntil) {
        this(lockName, ownerId, lockedUntil, lease -> {});
    }

    public RuntimeLockLease(
            String lockName,
            String ownerId,
            Instant lockedUntil,
            Consumer<RuntimeLockLease> releaser) {
        this.lockName = Objects.requireNonNull(lockName, "lockName");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.lockedUntil = Objects.requireNonNull(lockedUntil, "lockedUntil");
        this.releaser = Objects.requireNonNull(releaser, "releaser");
    }

    public String lockName() {
        return lockName;
    }

    public String ownerId() {
        return ownerId;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            releaser.accept(this);
        }
    }
}
