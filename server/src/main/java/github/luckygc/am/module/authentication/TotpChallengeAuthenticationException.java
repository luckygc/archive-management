package github.luckygc.am.module.authentication;

import java.io.Serial;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.AuthenticationException;

public class TotpChallengeAuthenticationException extends AuthenticationException {

    @Serial private static final long serialVersionUID = -1L;

    private final String errorCode;
    private final @Nullable String username;

    public TotpChallengeAuthenticationException(
            String errorCode, @Nullable String username, @Nullable Throwable cause) {
        super("账号或凭证错误", cause);
        this.errorCode = errorCode;
        this.username = username;
    }

    public String errorCode() {
        return errorCode;
    }

    public @Nullable String username() {
        return username;
    }
}
