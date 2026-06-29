package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.authentication.repository.LoginFailureLimitDataRepository;
import github.luckygc.am.module.authentication.service.PowChallengeService;
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
            "flowable.eventregistry.enabled=false",
            "archive.authentication.bootstrap-admin.enabled=true",
            "archive.authentication.bootstrap-admin.username=admin",
            "archive.authentication.bootstrap-admin.password=Admin123!",
            "archive.authentication.bootstrap-admin.display-name=系统管理员",
            "archive.authentication.login-failure-limit.window=10m",
            "archive.authentication.login-failure-limit.max-failures=2",
            "archive.authentication.login-failure-limit.initial-lock-duration=5m",
            "archive.authentication.login-failure-limit.multiplier=3",
            "archive.authentication.login-failure-limit.max-lock-duration=24h"
        })
@AutoConfigureMockMvc
@DisplayName("登录失败限制")
class LoginFailureLimitIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PowChallengeService powChallengeService;
    @Autowired private LoginFailureLimitDataRepository failureLimitRepository;

    @Test
    @DisplayName("连续失败达到阈值后锁定登录名且锁定期内不消费 CAP token")
    void locksUsernameAndDoesNotConsumeCapTokenWhileLocked() throws Exception {
        login("admin", "wrong-password", loginToken()).andExpect(status().isUnauthorized());
        login("admin", "wrong-password", loginToken()).andExpect(status().isUnauthorized());

        String reusableToken = loginToken();
        login("admin", "Admin123!", reusableToken)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("登录失败次数过多")));

        assertThat(powChallengeService.validateToken(reusableToken, true).get("success"))
                .isEqualTo(true);
    }

    @Test
    @DisplayName("未锁定时登录成功清除历史失败限制状态")
    void successfulLoginClearsFailureLimitState() throws Exception {
        LoginFailureLimit limit = new LoginFailureLimit();
        limit.setUsername("admin");
        limit.setFailureCount(1);
        limit.setLockLevel(0);
        limit.setFirstFailedAt(LocalDateTime.now().minusMinutes(1));
        limit.setLastFailedAt(LocalDateTime.now().minusMinutes(1));
        limit.setCleanupAfter(LocalDateTime.now().plusDays(1));
        failureLimitRepository.insert(limit);

        login("admin", "Admin123!", loginToken()).andExpect(status().isOk());

        assertThat(failureLimitRepository.findById("admin")).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String username, String password, String powToken) throws Exception {
        return mockMvc.perform(
                post("/api/v1/login-sessions")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password)
                        .param("powToken", powToken));
    }

    private String loginToken() {
        PowChallengeService.CapChallengeResponse challenge = powChallengeService.createChallenge();
        return powChallengeService
                .redeemChallenge(
                        new PowChallengeService.CapRedeemCommand(
                                challenge.token(),
                                AuthenticationSessionAuditIntegrationTests.solveCapChallenge(
                                        challenge)))
                .get("token")
                .toString();
    }
}
