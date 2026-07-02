package github.luckygc.am.module.authentication.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.authentication.AuthenticationCapToken;
import github.luckygc.am.module.authentication.PowChallengeException;
import github.luckygc.am.module.authentication.repository.AuthenticationCapChallengeDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationCapTokenDataRepository;

@DisplayName("CAP 安全验证服务")
class PowChallengeServiceTests {

    private AuthenticationCapChallengeDataRepository challengeRepository;
    private AuthenticationCapTokenDataRepository tokenRepository;
    private PowChallengeService powChallengeService;

    @BeforeEach
    void setUp() {
        challengeRepository = mock(AuthenticationCapChallengeDataRepository.class);
        tokenRepository = mock(AuthenticationCapTokenDataRepository.class);
        powChallengeService =
                new PowChallengeService(
                        challengeRepository, tokenRepository, mock(LoginFailureLimitService.class));
    }

    @Test
    @DisplayName("消费用户名绑定 CAP token 时使用原子删除作为唯一消费门")
    void consumeTokenShouldUseUsernameBoundAtomicDelete() {
        String rawToken = "token-id:raw-token";
        String tokenKey = "token-id:" + DigestUtils.sha256Hex("raw-token");
        String usernameHash = DigestUtils.sha256Hex("admin");
        when(tokenRepository.consume(eq(tokenKey), eq(usernameHash), any(LocalDateTime.class)))
                .thenReturn(1);

        powChallengeService.consumeToken(rawToken, "admin");

        verify(tokenRepository).consume(eq(tokenKey), eq(usernameHash), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("CAP token 原子消费失败时拒绝继续登录")
    void consumeTokenShouldRejectWhenAtomicDeleteMisses() {
        String rawToken = "token-id:raw-token";
        String tokenKey = "token-id:" + DigestUtils.sha256Hex("raw-token");
        String usernameHash = DigestUtils.sha256Hex("admin");
        AuthenticationCapToken staleToken = new AuthenticationCapToken();
        staleToken.setTokenKey(tokenKey);
        staleToken.setUsernameHash(usernameHash);
        staleToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(tokenRepository.findById(tokenKey)).thenReturn(Optional.of(staleToken));
        when(tokenRepository.consume(eq(tokenKey), eq(usernameHash), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> powChallengeService.consumeToken(rawToken, "admin"))
                .isInstanceOf(PowChallengeException.class);
    }
}
