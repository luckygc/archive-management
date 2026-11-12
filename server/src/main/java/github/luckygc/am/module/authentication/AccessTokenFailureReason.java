package github.luckygc.am.module.authentication;

public enum AccessTokenFailureReason {
    INVALID,
    EXPIRED,
    REVOKED,
    DISABLED,
    RATE_LIMITED
}
