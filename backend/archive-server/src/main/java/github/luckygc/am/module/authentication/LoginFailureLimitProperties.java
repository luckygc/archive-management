package github.luckygc.am.module.authentication;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.authentication.login-failure-limit")
public record LoginFailureLimitProperties(
        Duration window,
        int maxFailures,
        Duration initialLockDuration,
        int multiplier,
        Duration maxLockDuration,
        Duration cleanupDelay) {

    private static final Duration MAX_ALLOWED_LOCK_DURATION = Duration.ofMinutes(30);

    public LoginFailureLimitProperties {
        window = window == null ? Duration.ofMinutes(10) : window;
        maxFailures = maxFailures <= 0 ? 5 : maxFailures;
        initialLockDuration =
                initialLockDuration == null ? Duration.ofMinutes(5) : initialLockDuration;
        multiplier = multiplier <= 1 ? 3 : multiplier;
        maxLockDuration = maxLockDuration == null ? MAX_ALLOWED_LOCK_DURATION : maxLockDuration;
        if (maxLockDuration.compareTo(MAX_ALLOWED_LOCK_DURATION) > 0) {
            maxLockDuration = MAX_ALLOWED_LOCK_DURATION;
        }
        cleanupDelay = cleanupDelay == null ? Duration.ofHours(24) : cleanupDelay;
    }
}
