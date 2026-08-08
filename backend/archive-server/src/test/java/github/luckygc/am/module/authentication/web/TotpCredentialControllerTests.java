package github.luckygc.am.module.authentication.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import github.luckygc.am.module.authentication.ArchiveUserDetails;
import github.luckygc.am.module.authentication.service.TotpCredentialService;

@DisplayName("TOTP 凭据 HTTP 入口")
class TotpCredentialControllerTests {

    @Test
    @DisplayName("enrollment 响应禁止缓存且只返回当前用户的一次性绑定材料")
    void prepareEnrollmentShouldReturnNoStoreResponse() {
        TotpCredentialService service = mock(TotpCredentialService.class);
        TotpCredentialController controller = new TotpCredentialController(service);
        var expected =
                new TotpCredentialService.TotpEnrollmentResponse(
                        "token",
                        "manual",
                        "otpauth://local",
                        Instant.parse("2026-08-09T00:10:00Z"));
        when(service.prepareEnrollment(7L)).thenReturn(expected);

        var response = controller.prepareEnrollment(authentication());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isEqualTo(expected);
        verify(service).prepareEnrollment(7L);
    }

    @Test
    @DisplayName("确认启用后返回不含密钥的凭据状态资源")
    void createCredentialShouldReturnEnabledStatus() {
        TotpCredentialService service = mock(TotpCredentialService.class);
        TotpCredentialController controller = new TotpCredentialController(service);
        var request =
                new TotpCredentialService.CreateTotpCredentialRequest(
                        "enrollment", "Admin123!", "123456");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        var response = controller.createCredential(request, httpRequest, authentication());

        assertThat(response.totpEnabled()).isTrue();
        verify(service).createCredential(request, 7L, "admin", httpRequest);
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new ArchiveUserDetails(7L, "admin", "N/A", true, "管理员", List.of()),
                "N/A",
                List.of());
    }
}
