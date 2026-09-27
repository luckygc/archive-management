package github.luckygc.am.infrastructure.web;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.api.cursor-token")
public class CursorPageTokenProperties {

    private @Nullable String secret;

    public @Nullable String getSecret() {
        return secret;
    }

    public void setSecret(@Nullable String secret) {
        this.secret = secret;
    }
}
