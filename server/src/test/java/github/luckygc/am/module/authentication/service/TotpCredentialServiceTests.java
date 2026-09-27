package github.luckygc.am.module.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalLong;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import github.luckygc.am.module.authentication.AuthenticationTotpCredential;
import github.luckygc.am.module.authentication.AuthenticationTotpEnrollment;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpCredentialDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpEnrollmentDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpLoginChallengeDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("TOTP 凭据服务")
class TotpCredentialServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private AuthenticationTotpCredentialDataRepository credentialRepository;
    private AuthenticationTotpEnrollmentDataRepository enrollmentRepository;
    private AuthenticationTotpLoginChallengeDataRepository challengeRepository;
    private AuthenticationUserDataRepository userRepository;
    private AuthorizationPermissionService permissionService;
    private AuthenticationAuditService auditService;
    private LoginFailureLimitService failureLimitService;
    private TotpCodeService codeService;
    private PasswordEncoder passwordEncoder;
    private TextEncryptor textEncryptor;
    private TotpCredentialService service;

    @BeforeEach
    void setUp() {
        credentialRepository = mock(AuthenticationTotpCredentialDataRepository.class);
        enrollmentRepository = mock(AuthenticationTotpEnrollmentDataRepository.class);
        challengeRepository = mock(AuthenticationTotpLoginChallengeDataRepository.class);
        userRepository = mock(AuthenticationUserDataRepository.class);
        permissionService = mock(AuthorizationPermissionService.class);
        auditService = mock(AuthenticationAuditService.class);
        failureLimitService = mock(LoginFailureLimitService.class);
        codeService = mock(TotpCodeService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        textEncryptor = mock(TextEncryptor.class);
        service =
                new TotpCredentialService(
                        credentialRepository,
                        enrollmentRepository,
                        challengeRepository,
                        userRepository,
                        permissionService,
                        auditService,
                        failureLimitService,
                        codeService,
                        passwordEncoder,
                        textEncryptor,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("准备 enrollment 时替换旧状态且只持久化 token 摘要和密文")
    void prepareEnrollmentShouldReplacePendingStateAndStoreOnlyProtectedValues() {
        AuthenticationUser user = user();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(credentialRepository.findById(7L)).thenReturn(Optional.empty());
        when(codeService.generateSecret()).thenReturn("BASE32SECRET");
        when(codeService.provisioningUri("Archive Management", "admin", "BASE32SECRET"))
                .thenReturn("otpauth://local");
        when(textEncryptor.encrypt("BASE32SECRET")).thenReturn("v1:ciphertext");

        TotpCredentialService.TotpEnrollmentResponse response = service.prepareEnrollment(7L);

        ArgumentCaptor<AuthenticationTotpEnrollment> captor =
                ArgumentCaptor.forClass(AuthenticationTotpEnrollment.class);
        verify(enrollmentRepository).deleteByUserId(7L);
        verify(enrollmentRepository).insert(captor.capture());
        assertThat(captor.getValue().getTokenKey())
                .hasSize(64)
                .isNotEqualTo(response.enrollmentToken());
        assertThat(captor.getValue().getEncryptedSecret()).isEqualTo("v1:ciphertext");
        assertThat(response.manualKey()).isEqualTo("BASE32SECRET");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    @DisplayName("首次确认记录已接受时间步并立即阻止相同验证码重放")
    void createCredentialShouldPersistInitialAcceptedStepAndRejectReplay() {
        AuthenticationUser user = user();
        AuthenticationTotpEnrollment enrollment = new AuthenticationTotpEnrollment();
        enrollment.setTokenKey(org.apache.commons.codec.digest.DigestUtils.sha256Hex("enrollment"));
        enrollment.setUserId(7L);
        enrollment.setEncryptedSecret("v1:ciphertext");
        enrollment.setExpiresAt(
                java.time.LocalDateTime.ofInstant(
                        NOW.plusSeconds(60), java.time.ZoneId.systemDefault()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(credentialRepository.findById(7L)).thenReturn(Optional.empty());
        when(enrollmentRepository.findById(anyString())).thenReturn(Optional.of(enrollment));
        when(passwordEncoder.matches("Admin123!", "encoded-password")).thenReturn(true);
        when(textEncryptor.decrypt("v1:ciphertext")).thenReturn("BASE32SECRET");
        when(codeService.findMatchingStep("BASE32SECRET", "123456", NOW))
                .thenReturn(OptionalLong.of(100L));
        when(enrollmentRepository.consume(
                        eq(enrollment.getTokenKey()), eq(7L), any(java.time.LocalDateTime.class)))
                .thenReturn(1);

        service.createCredential(
                new TotpCredentialService.CreateTotpCredentialRequest(
                        "enrollment", "Admin123!", "123456"),
                7L,
                "admin",
                mock(HttpServletRequest.class));

        ArgumentCaptor<AuthenticationTotpCredential> captor =
                ArgumentCaptor.forClass(AuthenticationTotpCredential.class);
        verify(credentialRepository).insert(captor.capture());
        assertThat(captor.getValue().getLastAcceptedStep()).isEqualTo(100L);

        when(credentialRepository.findById(7L)).thenReturn(Optional.of(captor.getValue()));
        assertThat(service.verifyAndAdvance(7L, "123456")).isFalse();
        verify(credentialRepository, never())
                .advanceAcceptedStep(any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("管理员清除不依赖主密钥解密并一并失效未完成状态")
    void resetCredentialShouldNotDecryptSecret() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user()));

        service.resetCredential(7L, 9L, "operator", mock(HttpServletRequest.class));

        verify(credentialRepository).deleteByUserId(7L);
        verify(enrollmentRepository).deleteByUserId(7L);
        verify(challengeRepository).deleteByUserId(7L);
        verify(textEncryptor, never()).decrypt(any());
    }

    private AuthenticationUser user() {
        AuthenticationUser user = new AuthenticationUser();
        user.setId(7L);
        user.setUsername("admin");
        user.setPassword("encoded-password");
        user.setDisplayName("管理员");
        user.setEnabled(true);
        return user;
    }
}
