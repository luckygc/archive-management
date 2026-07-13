package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import jakarta.data.Order;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;
import jakarta.servlet.http.Cookie;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.authentication.repository.AuthenticationLoginLogDataRepository;
import github.luckygc.am.module.authentication.repository.SpringSessionRecordDataRepository;
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
            "archive.authentication.bootstrap-admin.display-name=系统管理员"
        })
@AutoConfigureMockMvc
@DisplayName("认证登录会话与审计")
class AuthenticationSessionAuditIntegrationTests extends PostgreSqlContainerTest {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.5 Safari/605.1.15";

    @Autowired private MockMvc mockMvc;
    @Autowired private PowChallengeService powChallengeService;
    @Autowired private AuthenticationLoginLogDataRepository loginLogRepository;
    @Autowired private SpringSessionRecordDataRepository springSessionRepository;

    @Test
    @DisplayName("登录成功写入审计并在登录会话列表展示客户端信息")
    void loginSuccessWritesAuditAndListsSession() throws Exception {
        MvcResult loginResult = login("admin", "Admin123!").andExpect(status().isOk()).andReturn();
        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        String sessionId = loginSessionId(loginResult);

        assertThat(sessionCookie).isNotNull();
        assertThat(
                        (String)
                                JsonPath.read(
                                        loginResult.getResponse().getContentAsString(),
                                        "$.creationTime"))
                .isNotBlank();
        assertThat(
                        (String)
                                JsonPath.read(
                                        loginResult.getResponse().getContentAsString(),
                                        "$.lastAccessTime"))
                .isNotBlank();
        assertThat(
                        (String)
                                JsonPath.read(
                                        loginResult.getResponse().getContentAsString(),
                                        "$.expiresAt"))
                .isNotBlank();
        assertThat(countLogs("login_success", sessionId)).isEqualTo(1);
        assertThat(firstLog("login_success", sessionId).getBrowserName()).isEqualTo("Safari");

        mockMvc.perform(withClientHeaders(get("/api/v1/login-sessions")).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("admin"))
                .andExpect(jsonPath("$.items[0].client.browserName").value("Safari"))
                .andExpect(jsonPath("$.items[0].request.host").value("archive.local:8080"));
    }

    @Test
    @DisplayName("登录失败写入失败审计且不创建登录会话")
    void loginFailureWritesAuditWithoutSession() throws Exception {
        long beforeFailureCount = countLogs("login_failure");
        long beforeActiveSessionCount = activeSessionCount();

        login("login-failure-user", "wrong-password").andExpect(status().isUnauthorized());

        assertThat(countLogs("login_failure")).isEqualTo(beforeFailureCount + 1);
        assertThat(activeSessionCount()).isEqualTo(beforeActiveSessionCount);
    }

    @Test
    @DisplayName("主动退出写入审计并删除当前会话")
    void logoutWritesAuditAndInvalidatesSession() throws Exception {
        MvcResult loginResult = login("admin", "Admin123!").andExpect(status().isOk()).andReturn();
        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        String sessionId = loginSessionId(loginResult);

        mockMvc.perform(
                        withClientHeaders(delete("/api/v1/login-sessions/{session}", sessionId))
                                .cookie(sessionCookie))
                .andExpect(status().isNoContent());

        assertThat(countLogs("logout")).isEqualTo(1);
        assertThat(sessionCount(sessionId)).isZero();
    }

    @Test
    @DisplayName("踢下线写入审计并删除目标会话")
    void revokeSessionWritesAuditAndDeletesTargetSession() throws Exception {
        MvcResult loginResult = login("admin", "Admin123!").andExpect(status().isOk()).andReturn();
        String sessionId = loginSessionId(loginResult);
        Cookie operatorCookie =
                login("admin", "Admin123!")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getCookie("SESSION");

        mockMvc.perform(
                        withClientHeaders(delete("/api/v1/login-sessions/{session}", sessionId))
                                .cookie(operatorCookie))
                .andExpect(status().isNoContent());

        assertThat(countLogs("kickout")).isEqualTo(1);
        assertThat(sessionCount(sessionId)).isZero();
    }

    @Test
    @DisplayName("认证审计 cursor 绑定查询条件，条件变化时拒绝旧 cursor")
    void authenticationEventCursorShouldBindQueryCondition() throws Exception {
        Cookie sessionCookie =
                login("admin", "Admin123!")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getCookie("SESSION");
        login("cursor-failure-user-1", "wrong-password").andExpect(status().isUnauthorized());
        login("cursor-failure-user-2", "wrong-password").andExpect(status().isUnauthorized());
        MvcResult firstPage =
                mockMvc.perform(
                                withClientHeaders(get("/api/v1/authentication-events"))
                                        .cookie(sessionCookie)
                                        .param("eventType", "login_failure")
                                        .param("limit", "1"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.next").isNotEmpty())
                        .andReturn();
        String next = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.next");

        mockMvc.perform(
                        withClientHeaders(get("/api/v1/authentication-events"))
                                .cookie(sessionCookie)
                                .param("eventType", "login_success")
                                .param("limit", "1")
                                .param("cursor", next))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String username, String password) throws Exception {
        String powToken = loginToken(username);
        return mockMvc.perform(
                withClientHeaders(post("/api/v1/login-sessions"))
                        .with(csrf())
                        .param("username", username)
                        .param("password", password)
                        .param("powToken", powToken));
    }

    private MockHttpServletRequestBuilder withClientHeaders(MockHttpServletRequestBuilder request) {
        return request.header("Host", "archive.local:8080")
                .header("Forwarded", "for=10.1.0.8;proto=https;host=archive.local")
                .header("X-Forwarded-For", "10.1.0.8, 10.1.0.9")
                .header("X-Real-IP", "10.1.0.8")
                .header("User-Agent", USER_AGENT)
                .with(csrf())
                .with(
                        servletRequest -> {
                            servletRequest.setRemoteAddr("10.1.0.10");
                            return servletRequest;
                        });
    }

    private String loginToken(String username) {
        PowChallengeService.CapChallengeResponse challenge =
                powChallengeService.createChallenge(
                        new PowChallengeService.CapChallengeRequest(username));
        var redeemResult =
                powChallengeService.redeemChallenge(
                        new PowChallengeService.CapRedeemRequest(
                                challenge.token(), solveCapChallenge(challenge)));
        return redeemResult.get("token").toString();
    }

    static List<Long> solveCapChallenge(PowChallengeService.CapChallengeResponse response) {
        List<Long> solutions = new ArrayList<>(response.challenge().c());
        for (int index = 0; index < response.challenge().c(); index++) {
            String salt = prng(response.token() + (index + 1), response.challenge().s());
            String target = prng(response.token() + (index + 1) + "d", response.challenge().d());
            long nonce = 0;
            while (!DigestUtils.sha256Hex(salt + nonce).startsWith(target)) {
                nonce++;
            }
            solutions.add(nonce);
        }
        return solutions;
    }

    private static String prng(String seed, int length) {
        long state = fnv1a(seed);
        StringBuilder result = new StringBuilder(length);
        while (result.length() < length) {
            state ^= (state << 13) & 0xffff_ffffL;
            state &= 0xffff_ffffL;
            state ^= state >>> 17;
            state &= 0xffff_ffffL;
            state ^= (state << 5) & 0xffff_ffffL;
            state &= 0xffff_ffffL;
            result.append("%08x".formatted(state));
        }
        return result.substring(0, length);
    }

    private static long fnv1a(String value) {
        long hash = 2166136261L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash =
                    (hash
                                    + ((hash << 1) & 0xffff_ffffL)
                                    + ((hash << 4) & 0xffff_ffffL)
                                    + ((hash << 7) & 0xffff_ffffL)
                                    + ((hash << 8) & 0xffff_ffffL)
                                    + ((hash << 24) & 0xffff_ffffL))
                            & 0xffff_ffffL;
        }
        return hash;
    }

    private String loginSessionId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.sessionId");
    }

    private long countLogs(String eventType) {
        return countLogs(_AuthenticationLoginLog.eventType.equalTo(eventType));
    }

    private long countLogs(String eventType, String sessionId) {
        return countLogs(
                Restrict.all(
                        _AuthenticationLoginLog.eventType.equalTo(eventType),
                        _AuthenticationLoginLog.sessionId.equalTo(sessionId)));
    }

    private long countLogs(Restriction<AuthenticationLoginLog> restriction) {
        return loginLogRepository
                .find(restriction, PageRequest.ofSize(1).withTotal())
                .totalElements();
    }

    private AuthenticationLoginLog firstLog(String eventType, String sessionId) {
        return loginLogRepository
                .find(
                        Restrict.all(
                                _AuthenticationLoginLog.eventType.equalTo(eventType),
                                _AuthenticationLoginLog.sessionId.equalTo(sessionId)),
                        PageRequest.ofSize(1).withoutTotal())
                .content()
                .getFirst();
    }

    private long sessionCount(String sessionId) {
        return springSessionRepository
                .find(
                        Restrict.all(
                                _SpringSessionRecord.sessionId.equalTo(sessionId),
                                _SpringSessionRecord.expiryTime.greaterThan(
                                        System.currentTimeMillis())),
                        PageRequest.ofSize(1).withTotal(),
                        Order.by(_SpringSessionRecord.lastAccessTime.desc()))
                .totalElements();
    }

    private long activeSessionCount() {
        return springSessionRepository
                .find(
                        _SpringSessionRecord.expiryTime.greaterThan(System.currentTimeMillis()),
                        PageRequest.ofSize(1).withTotal(),
                        Order.by(_SpringSessionRecord.lastAccessTime.desc()))
                .totalElements();
    }
}
