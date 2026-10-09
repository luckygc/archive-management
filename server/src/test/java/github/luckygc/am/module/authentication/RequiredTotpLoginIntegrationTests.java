package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpCredentialDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
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
            "archive.authentication.bootstrap-admin.password=Admin123!",
            "archive.authentication.totp.required=true",
            "archive.authentication.totp.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        })
@AutoConfigureMockMvc
@DisplayName("部署强制 TOTP 的首次登录")
class RequiredTotpLoginIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TotpCodeService codeService;
    @Autowired private AuthenticationUserDataRepository userRepository;
    @Autowired private AuthenticationTotpCredentialDataRepository credentialRepository;

    @Test
    @DisplayName("未绑定用户须保存密钥并通过验证码后才获得会话")
    void firstLoginRequiresEnrollmentAndVerification() throws Exception {
        MvcResult challenge =
                login().andExpect(status().isAccepted())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.manualKey").isNotEmpty())
                        .andExpect(jsonPath("$.otpauthUri").isNotEmpty())
                        .andReturn();
        String token = json(challenge, "$.challengeToken");
        String secret = json(challenge, "$.manualKey");
        Long userId = userRepository.findOptionalByUsername("admin").getId();
        assertThat(challenge.getResponse().getCookie("SESSION")).isNull();
        assertThat(credentialRepository.findById(userId)).isEmpty();
        mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());

        String code = codeService.generateCode(secret, Instant.now());
        verify(token, code.equals("000000") ? "111111" : "000000")
                .andExpect(status().isUnauthorized());
        assertThat(credentialRepository.findById(userId)).isEmpty();

        MvcResult completed = verify(token, code).andExpect(status().isOk()).andReturn();
        assertThat(completed.getResponse().getCookie("SESSION")).isNotNull();
        assertThat(credentialRepository.findById(userId)).isPresent();
        verify(token, code).andExpect(status().isUnauthorized());

        login().andExpect(status().isAccepted()).andExpect(jsonPath("$.manualKey").isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions login() throws Exception {
        return mockMvc.perform(
                post("/login-sessions")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "Admin123!"));
    }

    private org.springframework.test.web.servlet.ResultActions verify(String token, String code)
            throws Exception {
        return mockMvc.perform(
                post("/login-session-challenges:verifyTotp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"challengeToken":"%s","code":"%s"}
                                """
                                        .formatted(token, code)));
    }

    private String json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
