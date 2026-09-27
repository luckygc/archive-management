package github.luckygc.am.module.authentication;

import java.io.Serial;
import java.io.Serializable;

public record ClientRequestContext(
        String remoteAddress,
        String host,
        String forwarded,
        String xForwardedFor,
        String xRealIp,
        ClientInfo client)
        implements Serializable {

    @Serial private static final long serialVersionUID = -1L;
}
