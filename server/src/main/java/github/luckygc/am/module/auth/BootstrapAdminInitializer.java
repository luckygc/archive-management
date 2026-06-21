package github.luckygc.am.module.auth;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        prefix = "archive.auth.bootstrap-admin",
        name = "enabled",
        havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final BootstrapAdminProperties properties;
    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            JdbcClient jdbcClient,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        String username =
                requireText(properties.getUsername(), "archive.auth.bootstrap-admin.username");
        String password =
                requireText(properties.getPassword(), "archive.auth.bootstrap-admin.password");
        String displayName =
                requireText(
                        properties.getDisplayName(), "archive.auth.bootstrap-admin.display-name");

        boolean exists =
                jdbcClient
                        .sql("select exists(select 1 from am_auth_user where username = :username)")
                        .param("username", username)
                        .query((rs, rowNum) -> rs.getBoolean(1))
                        .single();
        if (exists) {
            return;
        }

        Long userId =
                jdbcClient
                        .sql(
                                """
                        insert into am_auth_user (username, password, display_name)
                        values (:username, :password, :displayName)
                        returning id
                        """)
                        .param("username", username)
                        .param("password", passwordEncoder.encode(password))
                        .param("displayName", displayName)
                        .query((rs, rowNum) -> rs.getLong("id"))
                        .single();

        jdbcClient
                .sql(
                        """
                        insert into am_auth_user_role_rel (user_id, role_id)
                        select :userId, id
                        from am_auth_role
                        where role_name in ('系统管理员', '系统监控')
                        on conflict do nothing
                        """)
                .param("userId", userId)
                .update();
    }

    private static String requireText(String value, String propertyName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException("缺少初始化管理员配置: " + propertyName);
        }
        return value.trim();
    }
}
