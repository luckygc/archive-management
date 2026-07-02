package github.luckygc.am.module.authentication.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        powChallengeService = new PowChallengeService(challengeRepository, tokenRepository);
    }

    @Test
    @DisplayName("消费 CAP token 时不绑定登录名")
    void consumeTokenShouldNotBindUsername() {
        String rawToken = "token-id:raw-token";
        String tokenKey = "token-id:" + DigestUtils.sha256Hex("raw-token");
        when(tokenRepository.consume(eq(tokenKey), any(LocalDateTime.class))).thenReturn(1);

        powChallengeService.consumeToken(rawToken, "operator");

        verify(tokenRepository).consume(eq(tokenKey), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("CAP token 原子消费失败时拒绝继续登录")
    void consumeTokenShouldRejectWhenAtomicDeleteMisses() {
        String rawToken = "token-id:raw-token";
        String tokenKey = "token-id:" + DigestUtils.sha256Hex("raw-token");
        when(tokenRepository.consume(eq(tokenKey), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> powChallengeService.consumeToken(rawToken, "admin"))
                .isInstanceOf(PowChallengeException.class);
    }

    @Test
    @DisplayName("创建 CAP challenge 时不写入登录名绑定信息")
    void createChallengeShouldNotBindUsername() {
        powChallengeService.createChallenge(new PowChallengeService.CapChallengeRequest("admin"));

        verify(challengeRepository)
                .insert(
                        org.mockito.ArgumentMatchers.argThat(
                                challenge -> challenge.getDifficulty() == 4));
        verifyNoInteractions(tokenRepository);
    }
}
