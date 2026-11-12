package github.luckygc.am.module.authentication;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/** 进程内固定窗口限流，按令牌 ID 计数。关闭配置后直接放行。 */
@Component
public class AccessTokenRateLimiter {

    private final AccessTokenRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    public AccessTokenRateLimiter(AccessTokenRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean tryAcquire(long tokenId) {
        if (!properties.isEnabled()) {
            return true;
        }
        int limit = Math.max(properties.getRequestsPerMinute(), 1);
        long window = clock.millis() / 60_000L;
        Window current =
                windows.compute(
                        tokenId,
                        (id, existing) ->
                                existing == null || existing.window != window
                                        ? new Window(window, new AtomicInteger(0))
                                        : existing);
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().window != window);
        }
        return current.count.incrementAndGet() <= limit;
    }

    private record Window(long window, AtomicInteger count) {}
}
