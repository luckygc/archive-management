package github.luckygc.am.module.authentication;

public enum AuthenticationLoginEventType {
    LOGIN_SUCCESS("login_success"),
    LOGIN_FAILURE("login_failure"),
    LOGOUT("logout"),
    KICKOUT("kickout"),
    TOTP_ENABLED("totp_enabled"),
    TOTP_DISABLED("totp_disabled"),
    TOTP_RESET("totp_reset");

    private final String value;

    AuthenticationLoginEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
