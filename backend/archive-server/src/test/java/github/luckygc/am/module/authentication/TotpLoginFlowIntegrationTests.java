package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.servlet.http.Cookie;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.authentication.repository.AuthenticationLoginLogDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpLoginChallengeDataRepository;
import github.luckygc.am.module.authentication.repository.LoginFailureLimitDataRepository;
import github.luckygc.am.module.authentication.repository.SpringSessionRecordDataRepository;
import github.luckygc.am.module.authentication.service.PowChallengeService;
import github.luckygc.am.module.authentication.service.TotpCodeService;
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
            "archive.authentication.totp.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        })
@AutoConfigureMockMvc
@DisplayName("TOTP 二阶段登录")
class TotpLoginFlowIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PowChallengeService powChallengeService;
    @Autowired private TotpCodeService totpCodeService;
    @Autowired private AuthenticationLoginLogDataRepository loginLogRepository;
    @Autowired private AuthenticationTotpLoginChallengeDataRepository challengeRepository;
    @Autowired private LoginFailureLimitDataRepository failureLimitRepository;
    @Autowired private SpringSessionRecordDataRepository springSessionRepository;

    @Test
    @DisplayName("启用后不提前创建会话或成功审计，首次验证码不可重放且验证成功轮换会话")
    void enabledUserShouldCompleteSecondStageBeforeSessionCreation() throws Exception {
        MvcResult initialLogin = login().andExpect(status().isOk()).andReturn();
        Cookie authenticatedSession = initialLogin.getResponse().getCookie("SESSION");
        assertThat(authenticatedSession).isNotNull();

        MvcResult enrollment =
                mockMvc.perform(
                                post("/api/v1/totp-enrollments")
                                        .cookie(authenticatedSession)
                                        .with(csrf()))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn();
        String enrollmentToken = json(enrollment, "$.enrollmentToken");
        String secret = json(enrollment, "$.manualKey");
        String initialCode = totpCodeService.generateCode(secret, Instant.now());

        mockMvc.perform(
                        post("/api/v1/totp-credentials")
                                .cookie(authenticatedSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"enrollmentToken":"%s","currentPassword":"Admin123!","code":"%s"}
                                        """
                                                .formatted(enrollmentToken, initialCode)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/me").cookie(authenticatedSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnabled").value(true));

        long sessionsBeforeChallenge = activeSessionCount();
        long successesBeforeChallenge = countLogs("login_success");
        MockHttpSession firstStageAnonymousSession = new MockHttpSession();
        MvcResult firstStage =
                login(firstStageAnonymousSession)
                        .andExpect(status().isAccepted())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.challengeToken").isNotEmpty())
                        .andReturn();
        String challengeToken = json(firstStage, "$.challengeToken");
        assertThat(firstStage.getResponse().getCookie("SESSION")).isNull();
        assertThat(
                        firstStageAnonymousSession.getAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isNull();
        assertThat(activeSessionCount()).isEqualTo(sessionsBeforeChallenge);
        assertThat(countLogs("login_success")).isEqualTo(successesBeforeChallenge);
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").session(firstStageAnonymousSession))
                .andExpect(status().isUnauthorized());

        verifyTotp(challengeToken, initialCode, new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_CODE_INVALID"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        assertThat(countLogs("login_success")).isEqualTo(successesBeforeChallenge);

        MvcResult competingFirstStage = login().andExpect(status().isAccepted()).andReturn();
        String competingChallengeToken = json(competingFirstStage, "$.challengeToken");

        String nextCode = totpCodeService.generateCode(secret, Instant.now().plusSeconds(30));
        MockHttpSession anonymousSession = new MockHttpSession();
        String anonymousSessionId = anonymousSession.getId();
        MvcResult completed =
                verifyTotp(challengeToken, nextCode, anonymousSession)
                        .andExpect(status().isOk())
                        .andReturn();

        assertThat(json(completed, "$.sessionId")).isNotEqualTo(anonymousSessionId);
        assertThat(completed.getResponse().getCookie("SESSION")).isNotNull();
        assertThat(countLogs("login_success")).isEqualTo(successesBeforeChallenge + 1);

        long failuresBeforeRejectedReplay = countLogs("login_failure");
        verifyTotp(competingChallengeToken, nextCode, new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_CODE_INVALID"));
        assertThat(countLogs("login_failure")).isEqualTo(failuresBeforeRejectedReplay + 1);
        assertThat(
                        challengeRepository
                                .findById(
                                        org.apache.commons.codec.digest.DigestUtils.sha256Hex(
                                                competingChallengeToken))
                                .orElseThrow()
                                .getFailedAttempts())
                .isEqualTo(1);
        assertThat(failureLimitRepository.findById("admin").orElseThrow().getFailureCount())
                .isEqualTo(1);

        verifyTotp(challengeToken, nextCode, new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_CHALLENGE_INVALID"));
    }

    private org.springframework.test.web.servlet.ResultActions login() throws Exception {
        return login(null);
    }

    private org.springframework.test.web.servlet.ResultActions login(
            @Nullable MockHttpSession session) throws Exception {
        PowChallengeService.CapChallengeResponse challenge =
                powChallengeService.createChallenge(
                        new PowChallengeService.CapChallengeRequest("admin"));
        String powToken =
                powChallengeService
                        .redeemChallenge(
                                new PowChallengeService.CapRedeemRequest(
                                        challenge.token(),
                                        AuthenticationSessionAuditIntegrationTests
                                                .solveCapChallenge(challenge)))
                        .get("token")
                        .toString();
        var request =
                post("/api/v1/login-sessions")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "Admin123!")
                        .param("powToken", powToken);
        if (session != null) {
            request.session(session);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions verifyTotp(
            String challengeToken, String code, MockHttpSession session) throws Exception {
        return mockMvc.perform(
                post("/api/v1/login-session-challenges:verifyTotp")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"challengeToken":"%s","code":"%s"}
                                """
                                        .formatted(challengeToken, code)));
    }

    private String json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private long countLogs(String eventType) {
        return loginLogRepository
                .find(eventType, PageRequest.ofSize(1).withTotal())
                .totalElements();
    }

    private long activeSessionCount() {
        return springSessionRepository
                .find(
                        Restrict.all(
                                _SpringSessionRecord.expiryTime.greaterThan(
                                        System.currentTimeMillis())),
                        PageRequest.ofSize(1).withTotal(),
                        jakarta.data.Order.by(_SpringSessionRecord.lastAccessTime.desc()))
                .totalElements();
    }
}
