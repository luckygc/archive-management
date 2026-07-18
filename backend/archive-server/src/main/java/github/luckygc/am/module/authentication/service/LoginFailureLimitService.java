package github.luckygc.am.module.authentication.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authentication.LoginBlockedException;
import github.luckygc.am.module.authentication.LoginFailureLimit;
import github.luckygc.am.module.authentication.LoginFailureLimitProperties;
import github.luckygc.am.module.authentication.mapper.LoginFailureLimitMapper;
import github.luckygc.am.module.authentication.repository.LoginFailureLimitDataRepository;

@Service
public class LoginFailureLimitService {

    private final LoginFailureLimitDataRepository repository;
    private final LoginFailureLimitMapper mapper;
    private final LoginFailureLimitProperties properties;

    public LoginFailureLimitService(
            LoginFailureLimitDataRepository repository,
            LoginFailureLimitMapper mapper,
            LoginFailureLimitProperties properties) {
        this.repository = repository;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public void assertLoginAllowed(@Nullable String rawUsername) {
        String username = normalizedUsername(rawUsername);
        if (username == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LoginFailureLimit limit = repository.findById(username).orElse(null);
        if (limit != null
                && limit.getLockedUntil() != null
                && limit.getLockedUntil().isAfter(now)) {
            throw new LoginBlockedException(limit.getLockedUntil());
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public void recordFailure(@Nullable String rawUsername) {
        String username = normalizedUsername(rawUsername);
        if (username == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int inserted =
                mapper.insertFirstFailureIfAbsent(
                        username, now, now.plus(properties.cleanupDelay()));
        if (inserted == 1) {
            if (properties.maxFailures() <= 1) {
                lockInsertedFirstFailure(username, now);
            }
            return;
        }
        LoginFailureLimit limit = mapper.findByUsernameForUpdate(username);
        if (limit == null) {
            return;
        }
        if (locked(limit, now)) {
            return;
        }
        if (expiredLock(limit, now) || outsideWindow(limit, now)) {
            resetWindow(limit, now);
        } else {
            limit.setFailureCount(limit.getFailureCount() + 1);
            limit.setLastFailedAt(now);
        }
        if (limit.getFailureCount() >= properties.maxFailures()) {
            lock(limit, now);
        }
        limit.setCleanupAfter(cleanupAfter(limit, now));
        mapper.update(limit);
    }

    private void lockInsertedFirstFailure(String username, LocalDateTime now) {
        LoginFailureLimit limit = mapper.findByUsernameForUpdate(username);
        if (limit == null || locked(limit, now)) {
            return;
        }
        lock(limit, now);
        limit.setCleanupAfter(cleanupAfter(limit, now));
        mapper.update(limit);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void clear(@Nullable String rawUsername) {
        String username = normalizedUsername(rawUsername);
        if (username != null && repository.findById(username).isPresent()) {
            repository.deleteById(username);
        }
    }

    private boolean locked(LoginFailureLimit limit, LocalDateTime now) {
        return limit.getLockedUntil() != null && limit.getLockedUntil().isAfter(now);
    }

    private boolean expiredLock(LoginFailureLimit limit, LocalDateTime now) {
        return limit.getLockedUntil() != null && !limit.getLockedUntil().isAfter(now);
    }

    private boolean outsideWindow(LoginFailureLimit limit, LocalDateTime now) {
        return limit.getFirstFailedAt().plus(properties.window()).isBefore(now);
    }

    private void resetWindow(LoginFailureLimit limit, LocalDateTime now) {
        limit.setFailureCount(1);
        limit.setFirstFailedAt(now);
        limit.setLastFailedAt(now);
        limit.setLockedUntil(null);
    }

    private void lock(LoginFailureLimit limit, LocalDateTime now) {
        Duration duration = lockDuration(limit.getLockLevel());
        limit.setLockedUntil(now.plus(duration));
        limit.setLockLevel(limit.getLockLevel() + 1);
    }

    private Duration lockDuration(int lockLevel) {
        Duration duration = properties.initialLockDuration();
        for (int index = 0; index < lockLevel; index++) {
            duration = duration.multipliedBy(properties.multiplier());
            if (duration.compareTo(properties.maxLockDuration()) >= 0) {
                return properties.maxLockDuration();
            }
        }
        return duration.compareTo(properties.maxLockDuration()) > 0
                ? properties.maxLockDuration()
                : duration;
    }

    private LocalDateTime cleanupAfter(LoginFailureLimit limit, LocalDateTime now) {
        return limit.getLastFailedAt().plus(properties.cleanupDelay());
    }

    private @Nullable String normalizedUsername(@Nullable String username) {
        return StringUtils.trimToNull(username);
    }
}
