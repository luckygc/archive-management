package github.luckygc.am.infrastructure.security.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.authentication.totp")
public record TotpEncryptionProperties(@Nullable String encryptionKey) {}
