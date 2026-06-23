package github.luckygc.am.module.auth;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    private final AuthUserDataRepository userRepository;
    private final AuthRoleDataRepository roleRepository;
    private final AuthUserRoleRelationDataRepository userRoleRelationRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            AuthUserDataRepository userRepository,
            AuthRoleDataRepository roleRepository,
            AuthUserRoleRelationDataRepository userRoleRelationRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
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

        if (userRepository.findOptionalByUsername(username) != null) {
            return;
        }

        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user = userRepository.insert(user);

        for (String roleName : properties.getRoleNames()) {
            grantRole(user.getId(), roleName);
        }
    }

    private void grantRole(Long userId, String roleName) {
        AuthRole role = roleRepository.findOptionalByRoleName(roleName);
        if (role == null) {
            return;
        }
        AuthUserRoleRelation relation = new AuthUserRoleRelation();
        relation.setUserId(userId);
        relation.setRoleId(role.getId());
        userRoleRelationRepository.insert(relation);
    }

    private static String requireText(String value, String propertyName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException("缺少初始化管理员配置: " + propertyName);
        }
        return value.trim();
    }
}
