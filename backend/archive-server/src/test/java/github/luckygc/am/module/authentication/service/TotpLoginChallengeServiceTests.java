package github.luckygc.am.module.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.authentication.AuthenticationTotpCredential;
import github.luckygc.am.module.authentication.AuthenticationTotpLoginChallenge;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.LoginBlockedException;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpCredentialDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpLoginChallengeDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;

@DisplayName("TOTP 登录挑战服务")
class TotpLoginChallengeServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private static final String TOKEN = "challenge-token";
    private static final String TOKEN_KEY =
            org.apache.commons.codec.digest.DigestUtils.sha256Hex(TOKEN);

    private AuthenticationTotpLoginChallengeDataRepository challengeRepository;
    private AuthenticationTotpCredentialDataRepository credentialRepository;
    private AuthenticationUserDataRepository userRepository;
    private DatabaseUserDetailsService userDetailsService;
    private TotpCredentialService credentialService;
    private LoginFailureLimitService failureLimitService;
    private TotpLoginChallengeService service;

    @BeforeEach
    void setUp() {
        challengeRepository = mock(AuthenticationTotpLoginChallengeDataRepository.class);
        credentialRepository = mock(AuthenticationTotpCredentialDataRepository.class);
        userRepository = mock(AuthenticationUserDataRepository.class);
        userDetailsService = mock(DatabaseUserDetailsService.class);
        credentialService = mock(TotpCredentialService.class);
        failureLimitService = mock(LoginFailureLimitService.class);
        service =
                new TotpLoginChallengeService(
                        challengeRepository,
                        credentialRepository,
                        userRepository,
                        userDetailsService,
                        credentialService,
                        failureLimitService,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("账号已受限时先丢弃挑战且不调用验证码验证器")
    void blockedAccountShouldDiscardChallengeBeforeTotpCalculation() {
        prepareValidState(challenge(0));
        org.mockito.Mockito.doThrow(new LoginBlockedException(LocalDateTime.now().plusMinutes(1)))
                .when(failureLimitService)
                .assertLoginAllowed("admin");

        TotpLoginChallengeService.VerificationResult result = service.verify(TOKEN, "123456");

        assertThat(result.errorCode()).isEqualTo("TOTP_CHALLENGE_INVALID");
        verify(challengeRepository).deleteById(TOKEN_KEY);
        verify(credentialService, never()).verifyAndAdvance(7L, "123456");
    }

    @Test
    @DisplayName("第五次错误验证码提交失败计数并立即丢弃挑战")
    void fifthInvalidCodeShouldPersistFailureAndDiscardChallenge() {
        prepareValidState(challenge(4));
        when(credentialService.verifyAndAdvance(7L, "123456")).thenReturn(false);

        TotpLoginChallengeService.VerificationResult result = service.verify(TOKEN, "123456");

        assertThat(result.errorCode()).isEqualTo("TOTP_CHALLENGE_INVALID");
        verify(challengeRepository)
                .recordFailure(TOKEN_KEY, LocalDateTime.ofInstant(NOW, ZoneId.systemDefault()), 5);
        verify(failureLimitService).recordFailure("admin");
        verify(challengeRepository).deleteById(TOKEN_KEY);
    }

    @Test
    @DisplayName("时间步条件更新未命中时按普通验证码错误提交两个失败计数")
    void rejectedStepAdvanceShouldPersistChallengeAndAccountFailures() {
        prepareValidState(challenge(0));
        when(credentialService.verifyAndAdvance(7L, "123456")).thenReturn(false);

        TotpLoginChallengeService.VerificationResult result = service.verify(TOKEN, "123456");

        assertThat(result.errorCode()).isEqualTo("TOTP_CODE_INVALID");
        verify(challengeRepository)
                .recordFailure(TOKEN_KEY, LocalDateTime.ofInstant(NOW, ZoneId.systemDefault()), 5);
        verify(failureLimitService).recordFailure("admin");
        verify(challengeRepository, never()).deleteById(TOKEN_KEY);
    }

    @Test
    @DisplayName("过期边界等于当前时间时丢弃挑战且不调用验证码验证器")
    void challengeExpiringNowShouldBeInvalidWithoutTotpCalculation() {
        AuthenticationTotpLoginChallenge expired = challenge(0);
        expired.setExpiresAt(LocalDateTime.ofInstant(NOW, ZoneId.systemDefault()));
        when(challengeRepository.findById(TOKEN_KEY)).thenReturn(Optional.of(expired));

        TotpLoginChallengeService.VerificationResult result = service.verify(TOKEN, "123456");

        assertThat(result.errorCode()).isEqualTo("TOTP_CHALLENGE_INVALID");
        verify(challengeRepository).deleteById(TOKEN_KEY);
        verify(credentialService, never()).verifyAndAdvance(7L, "123456");
        verify(failureLimitService, never()).recordFailure("admin");
    }

    private void prepareValidState(AuthenticationTotpLoginChallenge challenge) {
        AuthenticationUser user = new AuthenticationUser();
        user.setId(7L);
        user.setUsername("admin");
        user.setEnabled(true);
        when(challengeRepository.findById(TOKEN_KEY)).thenReturn(Optional.of(challenge));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(credentialRepository.findById(7L))
                .thenReturn(Optional.of(new AuthenticationTotpCredential()));
    }

    private AuthenticationTotpLoginChallenge challenge(int failedAttempts) {
        AuthenticationTotpLoginChallenge challenge = new AuthenticationTotpLoginChallenge();
        challenge.setTokenKey(TOKEN_KEY);
        challenge.setUserId(7L);
        challenge.setFailedAttempts(failedAttempts);
        challenge.setExpiresAt(
                LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneId.systemDefault()));
        return challenge;
    }
}
