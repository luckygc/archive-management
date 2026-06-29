package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.common.cleanup.ExpiredDataCleanupService;
import github.luckygc.am.module.authentication.repository.AuthenticationCapChallengeDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationCapTokenDataRepository;
import github.luckygc.am.module.authentication.repository.LoginFailureLimitDataRepository;
import github.luckygc.am.test.PostgreSqlContainerTest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.autoconfigure.exclude=org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false"
        })
@DisplayName("短生命周期数据统一清理")
class ExpiredDataCleanupIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private ExpiredDataCleanupService cleanupService;
    @Autowired private AuthenticationCapChallengeDataRepository challengeRepository;
    @Autowired private AuthenticationCapTokenDataRepository tokenRepository;
    @Autowired private LoginFailureLimitDataRepository failureLimitRepository;

    @Test
    @DisplayName("统一清理作业删除过期 CAP 和登录失败限制状态")
    void cleanupDeletesExpiredCapAndLoginFailureLimitState() {
        AuthenticationCapChallenge challenge = new AuthenticationCapChallenge();
        challenge.setToken("expired-challenge");
        challenge.setChallengeCount(1);
        challenge.setChallengeSize(1);
        challenge.setDifficulty(1);
        challenge.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        challengeRepository.insert(challenge);

        AuthenticationCapToken token = new AuthenticationCapToken();
        token.setTokenKey("expired-token");
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        tokenRepository.insert(token);

        LoginFailureLimit limit = new LoginFailureLimit();
        limit.setUsername("admin");
        limit.setFailureCount(2);
        limit.setLockLevel(1);
        limit.setFirstFailedAt(LocalDateTime.now().minusHours(2));
        limit.setLastFailedAt(LocalDateTime.now().minusHours(2));
        limit.setLockedUntil(LocalDateTime.now().minusHours(1));
        limit.setCleanupAfter(LocalDateTime.now().minusMinutes(1));
        failureLimitRepository.insert(limit);

        cleanupService.cleanupExpiredData();

        assertThat(challengeRepository.findById("expired-challenge")).isEmpty();
        assertThat(tokenRepository.findById("expired-token")).isEmpty();
        assertThat(failureLimitRepository.findById("admin")).isEmpty();
    }
}
