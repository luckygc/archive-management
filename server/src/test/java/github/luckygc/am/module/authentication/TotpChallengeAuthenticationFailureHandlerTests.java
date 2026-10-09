package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import github.luckygc.am.module.authentication.service.AuthenticationAuditService;
import github.luckygc.am.module.authentication.web.security.TotpChallengeAuthenticationFailureHandler;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("TOTP 登录挑战失败响应")
class TotpChallengeAuthenticationFailureHandlerTests {

    @Test
    @DisplayName("使用问题类型区分失效挑战且不返回旧错误代码")
    void expiredChallengeUsesProblemType() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        TotpChallengeAuthenticationFailureHandler handler =
                new TotpChallengeAuthenticationFailureHandler(
                        mapper, mock(AuthenticationAuditService.class));
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/login-session-challenges:verifyTotp");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new TotpChallengeAuthenticationException("TOTP_CHALLENGE_INVALID", null, null));

        var problem = mapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(problem.get("type").asText())
                .isEqualTo(
                        "https://github.com/luckygc/archive-management/blob/main/docs/api-problems.md#totp-challenge-invalid");
        assertThat(problem.get("instance").asText())
                .isEqualTo("/login-session-challenges:verifyTotp");
        assertThat(problem.has("code")).isFalse();
        assertThat(problem.has("reason")).isFalse();
    }
}
