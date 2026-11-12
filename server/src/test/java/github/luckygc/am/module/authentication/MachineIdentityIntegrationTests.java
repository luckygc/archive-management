package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.common.security.AccessTokenGrant;
import github.luckygc.am.common.security.AccessTokenGrantHolder;
import github.luckygc.am.common.security.AccessTokenSubjectType;
import github.luckygc.am.common.security.MachineAccessPrincipal;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.authentication.repository.AccessTokenDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
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
@DisplayName("机器身份")
class MachineIdentityIntegrationTests extends PostgreSqlContainerTest {

    private static final String EXPIRES_AT = "2027-01-01T00:00:00";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthenticationUserDataRepository userRepository;
    @Autowired private AccessTokenDataRepository tokenRepository;
    @Autowired private ArchiveDataScopeService dataScopeService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("服务账号令牌按角色权限和数据范围访问，并审计成功与失败")
    void serviceAccountTokenHonorsRoleScopeAndWritesAudit() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Cookie session = login();
        long securityContextsBefore = securityContextCount();
        long ownerId = userRepository.findOptionalByUsername("admin").getId();
        String fondsCode = "SA" + suffix.toUpperCase();

        resourceId(
                json(
                        post("/archive-fonds"),
                        """
                        {"fondsCode":"%s","fondsName":"机器身份全宗"}
                        """
                                .formatted(fondsCode),
                        session),
                201);
        long scopeId =
                resourceId(
                        json(
                                post("/archive-data-scopes"),
                                """
                                {"scopeCode":"sa-%s","scopeName":"机器全宗","scopeType":"CONDITIONAL","dimensions":[{"dimensionType":"FONDS","targetCode":"%s","includeDescendants":false}],"enabled":true}
                                """
                                        .formatted(suffix, fondsCode),
                                session),
                        200);
        long roleId =
                resourceId(
                        json(
                                post("/authorization-roles"),
                                """
                                {"roleName":"机器只读%s","description":"集成测试"}
                                """
                                        .formatted(suffix),
                                session),
                        201);
        json(
                        put("/authorization-roles/{role}/permissions", roleId),
                        """
                        {"permissionCodes":["archive:item:read"]}
                        """,
                        session)
                .andExpect(status().isOk());
        json(
                        put("/authorization-roles/{role}/archive-data-scopes", roleId),
                        """
                        {"scopeIds":[%d]}
                        """
                                .formatted(scopeId),
                        session)
                .andExpect(status().isOk());

        long accountId =
                resourceId(
                        json(
                                post("/service-accounts"),
                                """
                                {"name":"agent-%s","description":"只读调用","ownerUserId":%d,"roleIds":[%d]}
                                """
                                        .formatted(suffix, ownerId, roleId),
                                session),
                        201);
        MvcResult issued =
                json(
                                post("/service-accounts/{account}/access-tokens", accountId),
                                """
                                {"name":"reader","expiresAt":"%s"}
                                """
                                        .formatted(EXPIRES_AT),
                                session)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.secret").isNotEmpty())
                        .andExpect(jsonPath("$.scopes[0]").value("archive:item:read"))
                        .andExpect(jsonPath("$.tokenPrefix").isNotEmpty())
                        .andReturn();
        String secret = JsonPath.read(issued.getResponse().getContentAsString(), "$.secret");
        long tokenId = id(issued);

        json(
                        post("/service-accounts/{account}/access-tokens", accountId),
                        """
                        {"name":"too-wide","expiresAt":"%s","scopes":["authentication:user:manage"]}
                        """
                                .formatted(EXPIRES_AT),
                        session)
                .andExpect(status().isBadRequest());

        MvcResult bearerRead =
                mockMvc.perform(bearer(get("/archive-items"), secret))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(bearerRead.getResponse().getContentAsString()).doesNotContain(secret);
        mockMvc.perform(bearer(get("/me/permissions"), secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superAdmin").value(false))
                .andExpect(jsonPath("$.permissionCodes.length()").value(1))
                .andExpect(jsonPath("$.permissionCodes[0]").value("archive:item:read"));
        mockMvc.perform(bearer(get("/service-accounts"), secret)).andExpect(status().isForbidden());
        mockMvc.perform(
                        bearer(post("/archive-items:search"), secret)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post("/archive-items:search")
                                .cookie(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());
        assertThat(securityContextCount()).isEqualTo(securityContextsBefore);

        AccessTokenGrantHolder.set(
                new AccessTokenGrant(
                        tokenId,
                        AccessTokenSubjectType.SERVICE_ACCOUNT,
                        accountId,
                        null,
                        java.util.Set.of("archive:item:read")));
        try {
            ResolvedArchiveDataScope resolved =
                    dataScopeService.resolveUserDataScope(MachineAccessPrincipal.ACTOR_ID);
            assertThat(resolved.allData()).isFalse();
            assertThat(resolved.empty()).isFalse();
            assertThat(resolved.scopes())
                    .anySatisfy(
                            scope ->
                                    assertThat(scope.dimensions())
                                            .anySatisfy(
                                                    dimension ->
                                                            assertThat(dimension.getTargetCode())
                                                                    .isEqualTo(fondsCode)));
        } finally {
            AccessTokenGrantHolder.clear();
        }
        assertThat(dataScopeService.resolveUserDataScope(MachineAccessPrincipal.ACTOR_ID).empty())
                .isTrue();

        mockMvc.perform(bearer(get("/archive-items"), "am1_0123456789abcdef_not-real"))
                .andExpect(status().isUnauthorized());

        AccessToken expired =
                tokenRepository.findByTokenPrefix(AccessTokenSecrets.prefixOf(secret));
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        tokenRepository.update(expired);
        mockMvc.perform(bearer(get("/archive-items"), secret)).andExpect(status().isUnauthorized());

        MvcResult second =
                json(
                                post("/service-accounts/{account}/access-tokens", accountId),
                                """
                                {"name":"revoked","expiresAt":"%s","scopes":["archive:item:read"]}
                                """
                                        .formatted(EXPIRES_AT),
                                session)
                        .andExpect(status().isCreated())
                        .andReturn();
        String secondSecret = JsonPath.read(second.getResponse().getContentAsString(), "$.secret");
        mockMvc.perform(post("/access-tokens/{id}:revoke", id(second)).cookie(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(bearer(get("/archive-items"), secondSecret))
                .andExpect(status().isUnauthorized());

        MvcResult third =
                json(
                                post("/service-accounts/{account}/access-tokens", accountId),
                                """
                                {"name":"disabled","expiresAt":"%s","scopes":["archive:item:read"]}
                                """
                                        .formatted(EXPIRES_AT),
                                session)
                        .andExpect(status().isCreated())
                        .andReturn();
        String thirdSecret = JsonPath.read(third.getResponse().getContentAsString(), "$.secret");
        mockMvc.perform(
                        patch("/service-accounts/{id}", accountId)
                                .cookie(session)
                                .with(csrf())
                                .contentType("application/merge-patch+json")
                                .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(bearer(get("/archive-items"), thirdSecret))
                .andExpect(status().isUnauthorized());

        MvcResult calls =
                mockMvc.perform(
                                get("/access-token-calls")
                                        .cookie(session)
                                        .with(csrf())
                                        .param("limit", "100"))
                        .andExpect(status().isOk())
                        .andReturn();
        String callBody = calls.getResponse().getContentAsString();
        List<String> reasons = JsonPath.read(callBody, "$.items[*].failureReason");
        assertThat(reasons).contains("INVALID", "EXPIRED", "REVOKED", "DISABLED");
        List<Integer> accountStatuses =
                JsonPath.read(
                        callBody, "$.items[?(@.serviceAccountId == " + accountId + ")].statusCode");
        assertThat(accountStatuses).contains(200, 401, 403);
        assertThat(callBody).doesNotContain(secret);

        mockMvc.perform(
                        post("/login-sessions")
                                .with(csrf())
                                .param("username", "admin")
                                .param("password", "Admin123!"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("个人访问令牌权限是用户权限与 scope 的交集")
    void personalAccessTokenIntersectsUserPermissions() throws Exception {
        Cookie session = login();
        MvcResult issued =
                json(
                                post("/me/access-tokens"),
                                """
                                {"name":"personal","expiresAt":"%s","scopes":["archive:item:read"]}
                                """
                                        .formatted(EXPIRES_AT),
                                session)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.tokenType").value("USER"))
                        .andReturn();
        String secret = JsonPath.read(issued.getResponse().getContentAsString(), "$.secret");
        mockMvc.perform(bearer(get("/me/permissions"), secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superAdmin").value(false))
                .andExpect(jsonPath("$.permissionCodes.length()").value(1))
                .andExpect(jsonPath("$.permissionCodes[0]").value("archive:item:read"));
        mockMvc.perform(bearer(get("/service-accounts"), secret)).andExpect(status().isForbidden());
        json(
                        post("/me/access-tokens"),
                        """
                        {"name":"unknown","expiresAt":"%s","scopes":["not:a:permission"]}
                        """
                                .formatted(EXPIRES_AT),
                        session)
                .andExpect(status().isBadRequest());
    }

    private Cookie login() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/login-sessions")
                                        .with(csrf())
                                        .param("username", "admin")
                                        .param("password", "Admin123!"))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) {
            cookie = result.getResponse().getCookie("am_session");
        }
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private org.springframework.test.web.servlet.ResultActions json(
            MockHttpServletRequestBuilder request, String body, Cookie session) throws Exception {
        return mockMvc.perform(
                request.cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private MockHttpServletRequestBuilder bearer(
            MockHttpServletRequestBuilder request, String secret) {
        return request.header("Authorization", "Bearer " + secret)
                .with(
                        servletRequest -> {
                            servletRequest.setRemoteAddr("10.8.0.8");
                            return servletRequest;
                        });
    }

    private long resourceId(org.springframework.test.web.servlet.ResultActions result, int status)
            throws Exception {
        return id(result.andExpect(status().is(status)).andReturn());
    }

    private long id(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long securityContextCount() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "select count(*) from spring_session_attributes where attribute_name = ?",
                        Integer.class,
                        "SPRING_SECURITY_CONTEXT");
        return count == null ? 0 : count;
    }
}
