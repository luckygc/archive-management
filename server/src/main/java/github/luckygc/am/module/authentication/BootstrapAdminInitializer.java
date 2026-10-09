package github.luckygc.am.module.authentication;

import java.security.SecureRandom;
import java.util.Base64;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationUserRoleService;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BootstrapAdminProperties properties;
    private final AuthenticationUserDataRepository userRepository;
    private final AuthorizationUserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            AuthenticationUserDataRepository userRepository,
            AuthorizationUserRoleService userRoleService,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.userRoleService = userRoleService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        String username =
                requireText(
                        properties.getUsername(),
                        "archive.authentication.bootstrap-admin.username");
        String displayName =
                requireText(
                        properties.getDisplayName(),
                        "archive.authentication.bootstrap-admin.display-name");

        AuthenticationUser user = userRepository.findOptionalByUsername(username);
        if (user == null) {
            String configuredPassword = StringUtils.trimToNull(properties.getPassword());
            String password = configuredPassword == null ? randomPassword() : configuredPassword;
            user = new AuthenticationUser();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setDisplayName(displayName);
            user = userRepository.insert(user);
            if (configuredPassword == null) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                log.warn("内置管理员账号 {} 的初始密码：{}。请首次登录后立即修改", username, password);
                            }
                        });
            }
        }

        for (String roleName : properties.getRoleNames()) {
            userRoleService.grantConfiguredRole(user.getId(), roleName);
        }
    }

    private static String requireText(String value, String propertyName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException("缺少初始化管理员配置: " + propertyName);
        }
        return value.trim();
    }

    private static String randomPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
