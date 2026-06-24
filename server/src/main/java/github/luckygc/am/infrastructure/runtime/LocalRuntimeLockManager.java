package github.luckygc.am.infrastructure.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;

import github.luckygc.am.common.runtime.RuntimeLockAdapter;
import github.luckygc.am.common.runtime.RuntimeLockLease;

class LocalRuntimeLockManager implements RuntimeLockAdapter {

    private final ConcurrentMap<String, RuntimeLockLease> leases = new ConcurrentHashMap<>();

    @Override
    public String adapter() {
        return "local";
    }

    @Override
    public Optional<RuntimeLockLease> tryLock(
            String lockName, String ownerId, Duration leaseDuration) {
        String normalizedLockName = requireText(lockName, "锁名称");
        String normalizedOwnerId = requireText(ownerId, "锁持有者");
        requireLeaseSeconds(leaseDuration);
        Instant now = Instant.now();
        RuntimeLockLease nextLease =
                new RuntimeLockLease(
                        normalizedLockName,
                        normalizedOwnerId,
                        now.plus(leaseDuration),
                        this::release);
        RuntimeLockLease lease =
                leases.compute(
                        normalizedLockName,
                        (key, current) -> {
                            if (current == null
                                    || current.lockedUntil().isBefore(now)
                                    || current.ownerId().equals(normalizedOwnerId)) {
                                return nextLease;
                            }
                            return current;
                        });
        return nextLease.equals(lease) ? Optional.of(nextLease) : Optional.empty();
    }

    @Override
    public void unlock(RuntimeLockLease lease) {
        lease.close();
    }

    private void release(RuntimeLockLease lease) {
        leases.remove(lease.lockName(), lease);
    }

    private String requireText(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private void requireLeaseSeconds(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.toSeconds() < 1) {
            throw new IllegalArgumentException("锁租约必须至少 1 秒");
        }
    }
}
