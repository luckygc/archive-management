package github.luckygc.am.module.authentication;

import java.time.Duration;
import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginFailureLimitService {

    private final LoginFailureLimitDataRepository repository;
    private final LoginFailureLimitProperties properties;

    public LoginFailureLimitService(
            LoginFailureLimitDataRepository repository, LoginFailureLimitProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public void assertLoginAllowed(@Nullable String rawUsername) {
        String username = normalizedUsername(rawUsername);
        if (username == null) {
            return;
        }
        LoginFailureLimit limit = repository.findById(username).orElse(null);
        LocalDateTime now = LocalDateTime.now();
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
        LoginFailureLimit limit = repository.findById(username).orElse(null);
        if (limit == null) {
            insertFirstFailure(username, now);
            return;
        }
        if (outsideWindow(limit, now)) {
            resetWindow(limit, now);
        } else {
            limit.setFailureCount(limit.getFailureCount() + 1);
            limit.setLastFailedAt(now);
        }
        if (limit.getFailureCount() >= properties.maxFailures()) {
            int nextLevel = limit.getLockLevel() + 1;
            limit.setLockLevel(nextLevel);
            limit.setLockedUntil(now.plus(lockDuration(nextLevel)));
        }
        limit.setCleanupAfter(cleanupAfter(limit, now));
        repository.update(limit);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void clear(@Nullable String rawUsername) {
        String username = normalizedUsername(rawUsername);
        if (username != null && repository.findById(username).isPresent()) {
            repository.deleteById(username);
        }
    }

    private void insertFirstFailure(String username, LocalDateTime now) {
        LoginFailureLimit limit = new LoginFailureLimit();
        limit.setUsername(username);
        limit.setFailureCount(1);
        limit.setLockLevel(0);
        limit.setFirstFailedAt(now);
        limit.setLastFailedAt(now);
        limit.setCleanupAfter(now.plus(properties.cleanupDelay()));
        repository.insert(limit);
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

    private Duration lockDuration(int level) {
        long multiplier = 1;
        for (int index = 1; index < level; index++) {
            multiplier =
                    Math.min(
                            Long.MAX_VALUE / properties.multiplier(),
                            multiplier * properties.multiplier());
        }
        Duration duration = properties.initialLockDuration().multipliedBy(multiplier);
        return duration.compareTo(properties.maxLockDuration()) > 0
                ? properties.maxLockDuration()
                : duration;
    }

    private LocalDateTime cleanupAfter(LoginFailureLimit limit, LocalDateTime now) {
        LocalDateTime base =
                limit.getLockedUntil() != null && limit.getLockedUntil().isAfter(now)
                        ? limit.getLockedUntil()
                        : limit.getLastFailedAt();
        return base.plus(properties.cleanupDelay());
    }

    private @Nullable String normalizedUsername(@Nullable String username) {
        return StringUtils.trimToNull(username);
    }
}
