package github.luckygc.am.module.authentication;

public enum AuthenticationLoginEventType {
    LOGIN_SUCCESS("login_success"),
    LOGIN_FAILURE("login_failure"),
    LOGOUT("logout"),
    KICKOUT("kickout");

    private final String value;

    AuthenticationLoginEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
