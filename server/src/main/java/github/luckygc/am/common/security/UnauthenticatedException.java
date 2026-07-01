package github.luckygc.am.common.security;

import java.io.Serial;

public class UnauthenticatedException extends RuntimeException {

    @Serial private static final long serialVersionUID = -1L;

    public UnauthenticatedException() {
        super("请先登录");
    }
}
