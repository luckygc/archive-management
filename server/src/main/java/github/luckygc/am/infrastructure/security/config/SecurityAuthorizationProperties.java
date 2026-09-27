package github.luckygc.am.infrastructure.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "archive.security.authorization")
public class SecurityAuthorizationProperties {

    private String actuatorRoleName = "系统监控";
}
