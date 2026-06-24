package github.luckygc.am.infrastructure.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import github.luckygc.am.common.runtime.RuntimeLockAdapter;
import github.luckygc.am.common.runtime.RuntimeLockLease;

class DatabaseRuntimeLockManager implements RuntimeLockAdapter {

    private final RuntimeMapper runtimeMapper;

    DatabaseRuntimeLockManager(RuntimeMapper runtimeMapper) {
        this.runtimeMapper = runtimeMapper;
    }

    @Override
    public String adapter() {
        return "database";
    }

    @Override
    public Optional<RuntimeLockLease> tryLock(
            String lockName, String ownerId, Duration leaseDuration) {
        String normalizedLockName = requireText(lockName, "锁名称");
        String normalizedOwnerId = requireText(ownerId, "锁持有者");
        long leaseSeconds = requireLeaseSeconds(leaseDuration);
        int updated =
                runtimeMapper.updateRuntimeLock(
                        normalizedLockName, normalizedOwnerId, leaseSeconds);
        int inserted =
                updated > 0
                        ? 0
                        : runtimeMapper.insertRuntimeLock(
                                normalizedLockName, normalizedOwnerId, leaseSeconds);
        if (updated + inserted == 0) {
            return Optional.empty();
        }
        return Optional.of(
                new RuntimeLockLease(
                        normalizedLockName,
                        normalizedOwnerId,
                        Instant.now().plusSeconds(leaseSeconds),
                        this::release));
    }

    @Override
    public void unlock(RuntimeLockLease lease) {
        lease.close();
    }

    private void release(RuntimeLockLease lease) {
        runtimeMapper.releaseRuntimeLock(lease.lockName(), lease.ownerId());
    }

    private String requireText(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private long requireLeaseSeconds(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.toSeconds() < 1) {
            throw new IllegalArgumentException("锁租约必须至少 1 秒");
        }
        return leaseDuration.toSeconds();
    }
}
