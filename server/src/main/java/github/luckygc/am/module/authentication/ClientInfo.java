package github.luckygc.am.module.authentication;

import java.io.Serial;
import java.io.Serializable;

public record ClientInfo(
        String userAgent,
        String browserName,
        String browserVersion,
        String osName,
        String osVersion,
        String deviceType)
        implements Serializable {

    @Serial private static final long serialVersionUID = -1L;
}
