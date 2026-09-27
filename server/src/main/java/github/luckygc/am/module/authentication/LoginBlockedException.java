package github.luckygc.am.module.authentication;

import java.io.Serial;
import java.time.LocalDateTime;

public class LoginBlockedException extends RuntimeException {

    @Serial private static final long serialVersionUID = -1L;

    private final LocalDateTime lockedUntil;

    public LoginBlockedException(LocalDateTime lockedUntil) {
        super("登录失败次数过多，请稍后再试");
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime lockedUntil() {
        return lockedUntil;
    }
}
