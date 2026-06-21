package github.luckygc.am.module.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "archive.auth.bootstrap-admin")
public class BootstrapAdminProperties {

    private boolean enabled = false;

    private String username = "admin";

    private String password = "";

    private String displayName = "系统管理员";
}
