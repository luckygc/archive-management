package github.luckygc.am.infrastructure.security.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "archive.security.request-signature")
public class ApiRequestSignatureProperties {

    private boolean enabled = false;

    private String secret;

    private Duration clockSkew = Duration.ofMinutes(5);
}
