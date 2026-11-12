package github.luckygc.am.module.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "archive.security.access-token.rate-limit")
public class AccessTokenRateLimitProperties {

    private boolean enabled = true;

    private int requestsPerMinute = 300;
}
