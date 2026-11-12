package github.luckygc.am.common.security;

/**
 * 服务账号认证主体。它不是用户，{@link #id()} 保持为空，避免把服务账号 ID 写进用户外键。
 * 需要一个非空占位符才能复用现有 {@code userId} 参数的接口时，使用 {@link #ACTOR_ID}。
 */
public final class MachineAccessPrincipal implements AuthenticatedUser {

    /** 不会与数据库用户主键冲突的占位符。权限和数据范围必须读取令牌约束，不能把该值当作用户 ID。 */
    public static final long ACTOR_ID = -1L;

    private final long serviceAccountId;
    private final String displayName;

    public MachineAccessPrincipal(long serviceAccountId, String displayName) {
        this.serviceAccountId = serviceAccountId;
        this.displayName = displayName;
    }

    @Override
    public Long id() {
        return null;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    public long serviceAccountId() {
        return serviceAccountId;
    }
}
