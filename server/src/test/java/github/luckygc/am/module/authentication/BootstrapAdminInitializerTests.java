package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationUserRoleService;

@DisplayName("内置管理员初始化")
class BootstrapAdminInitializerTests {

    private final AuthenticationUserDataRepository userRepository =
            mock(AuthenticationUserDataRepository.class);
    private final AuthorizationRoleDataRepository roleRepository =
            mock(AuthorizationRoleDataRepository.class);
    private final AuthorizationUserRoleRelationDataRepository relationRepository =
            mock(AuthorizationUserRoleRelationDataRepository.class);
    private final PasswordEncoder passwordEncoder =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final BootstrapAdminProperties properties = new BootstrapAdminProperties();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("无指定密码时生成随机密码哈希并仅在提交后安排输出")
    void createsRandomPasswordForAbsentAdmin() throws Exception {
        properties.setRoleNames(List.of());
        TransactionSynchronizationManager.initSynchronization();
        var initializer =
                new BootstrapAdminInitializer(
                        properties,
                        userRepository,
                        new AuthorizationUserRoleService(roleRepository, relationRepository),
                        passwordEncoder);

        initializer.run(new DefaultApplicationArguments());

        var captured = org.mockito.ArgumentCaptor.forClass(AuthenticationUser.class);
        verify(userRepository).insert(captured.capture());
        assertThat(captured.getValue().getUsername()).isEqualTo("admin");
        assertThat(captured.getValue().getPassword()).startsWith("{bcrypt}");
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    @DisplayName("已有账号不会重置密码或输出新初始密码")
    void keepsExistingAdminPassword() throws Exception {
        properties.setRoleNames(List.of());
        AuthenticationUser existing = new AuthenticationUser();
        existing.setId(1L);
        existing.setUsername("admin");
        existing.setPassword("existing-hash");
        when(userRepository.findOptionalByUsername("admin")).thenReturn(existing);
        TransactionSynchronizationManager.initSynchronization();
        var initializer =
                new BootstrapAdminInitializer(
                        properties,
                        userRepository,
                        new AuthorizationUserRoleService(roleRepository, relationRepository),
                        passwordEncoder);

        initializer.run(new DefaultApplicationArguments());

        verify(userRepository, never()).insert(any());
        assertThat(existing.getPassword()).isEqualTo("existing-hash");
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }
}
