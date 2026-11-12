package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("访问令牌限流")
class AccessTokenRateLimiterTests {

    @Test
    @DisplayName("同一分钟超过限额后拒绝，进入下一分钟后重新计数")
    void fixedWindowRejectsThenResets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:10Z"));
        AccessTokenRateLimiter limiter = limiter(clock, true, 2);

        assertThat(limiter.tryAcquire(7L)).isTrue();
        assertThat(limiter.tryAcquire(7L)).isTrue();
        assertThat(limiter.tryAcquire(7L)).isFalse();
        assertThat(limiter.tryAcquire(8L)).isTrue();

        clock.instant = clock.instant.plusSeconds(60);
        assertThat(limiter.tryAcquire(7L)).isTrue();
    }

    @Test
    @DisplayName("关闭配置后不再计数")
    void disabledLimiterAlwaysAllows() {
        AccessTokenRateLimiter limiter =
                limiter(
                        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                        false,
                        1);

        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isTrue();
    }

    private static AccessTokenRateLimiter limiter(
            Clock clock, boolean enabled, int requestsPerMinute) {
        AccessTokenRateLimitProperties properties = new AccessTokenRateLimitProperties();
        properties.setEnabled(enabled);
        properties.setRequestsPerMinute(requestsPerMinute);
        return new AccessTokenRateLimiter(properties, clock);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
