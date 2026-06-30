package github.luckygc.am.module.authentication.service;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authentication.LoginFailureLimit;
import github.luckygc.am.module.authentication.LoginFailureLimitProperties;
import github.luckygc.am.module.authentication.repository.LoginFailureLimitDataRepository;

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
        // 登录失败状态只用于提高后续 PoW 成本，不再锁定账号。
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
        limit.setCleanupAfter(cleanupAfter(limit, now));
        repository.update(limit);
    }

    @Transactional(readOnly = true)
    public int difficulty(@Nullable String rawUsername, int baseDifficulty, int maxDifficulty) {
        String username = normalizedUsername(rawUsername);
        if (username == null) {
            return baseDifficulty;
        }
        LoginFailureLimit limit = repository.findById(username).orElse(null);
        if (limit == null) {
            return baseDifficulty;
        }
        int increments = Math.min(2, Math.max(1, limit.getFailureCount()));
        return Math.min(maxDifficulty, baseDifficulty + increments);
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

    private LocalDateTime cleanupAfter(LoginFailureLimit limit, LocalDateTime now) {
        return limit.getLastFailedAt().plus(properties.cleanupDelay());
    }

    private @Nullable String normalizedUsername(@Nullable String username) {
        return StringUtils.trimToNull(username);
    }
}
