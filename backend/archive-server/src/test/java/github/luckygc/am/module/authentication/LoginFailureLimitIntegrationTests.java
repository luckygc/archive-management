package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
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
            "archive.authentication.login-failure-limit.max-lock-duration=30m"
        })
@AutoConfigureMockMvc
@DisplayName("登录失败限制")
class LoginFailureLimitIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PowChallengeService powChallengeService;
    @Autowired private LoginFailureLimitDataRepository failureLimitRepository;

    @BeforeEach
    void resetFailureLimitState() {
        failureLimitRepository
                .findById("admin")
                .ifPresent(limit -> failureLimitRepository.deleteById("admin"));
    }

    @Test
    @DisplayName("连续失败后锁定登录名但不提高 CAP 难度")
    void failuresLockUsernameWithoutIncreasingCapDifficulty() throws Exception {
        login("admin", "wrong-password", loginToken()).andExpect(status().isUnauthorized());
        login("admin", "wrong-password", loginToken()).andExpect(status().isUnauthorized());

        PowChallengeService.CapChallengeResponse challenge =
                powChallengeService.createChallenge(
                        new PowChallengeService.CapChallengeRequest("admin"));

        assertThat(challenge.challenge().d()).isEqualTo(4);
        login("admin", "Admin123!", loginToken()).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("登录成功清除历史失败限制状态")
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

    @Test
    @DisplayName("未绑定登录名的 CAP token 可以用于登录")
    void unboundCapTokenCanBeUsedForLogin() throws Exception {
        String unboundToken = unboundLoginToken();

        login("admin", "Admin123!", unboundToken).andExpect(status().isOk());

        assertThat(powChallengeService.validateToken(unboundToken, true).get("success"))
                .isEqualTo(false);
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String username, String password, String powToken) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                post("/api/v1/login-sessions")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password)
                        .param("powToken", powToken)
                        .header("User-Agent", "Mozilla/5.0 Test Browser");
        return mockMvc.perform(request);
    }

    private String loginToken() {
        PowChallengeService.CapChallengeResponse challenge =
                powChallengeService.createChallenge(
                        new PowChallengeService.CapChallengeRequest("admin"));
        return redeemToken(challenge);
    }

    private String unboundLoginToken() {
        return redeemToken(powChallengeService.createChallenge());
    }

    private String redeemToken(PowChallengeService.CapChallengeResponse challenge) {
        return powChallengeService
                .redeemChallenge(
                        new PowChallengeService.CapRedeemRequest(
                                challenge.token(),
                                AuthenticationSessionAuditIntegrationTests.solveCapChallenge(
                                        challenge)))
                .get("token")
                .toString();
    }
}
