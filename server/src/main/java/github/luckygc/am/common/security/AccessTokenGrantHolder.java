package github.luckygc.am.common.security;

import org.jspecify.annotations.Nullable;

/** 请求线程上的令牌约束。由 Bearer 过滤器设置，并在请求结束时清除。 */
public final class AccessTokenGrantHolder {

    private static final ThreadLocal<AccessTokenGrant> CURRENT = new ThreadLocal<>();

    private AccessTokenGrantHolder() {}

    public static void set(@Nullable AccessTokenGrant grant) {
        if (grant == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(grant);
    }

    public static @Nullable AccessTokenGrant current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
