package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("客户端请求上下文解析")
class ClientRequestContextResolverTests {

    private final ClientRequestContextResolver resolver = new ClientRequestContextResolver();

    @Test
    @DisplayName("采集请求头并解析常见浏览器、系统和设备类型")
    void resolvesHeadersAndUserAgentSummary() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/login-sessions");
        request.setRemoteAddr("10.0.0.12");
        request.setServerName("archive.local");
        request.addHeader("Host", "archive.local:8080");
        request.addHeader("Forwarded", "for=10.0.0.10;proto=https;host=archive.example");
        request.addHeader("X-Forwarded-For", "10.0.0.10, 10.0.0.11");
        request.addHeader("X-Real-IP", "10.0.0.10");
        request.addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");

        ClientRequestContext context = resolver.resolve(request);

        assertThat(context.remoteAddress()).isEqualTo("10.0.0.12");
        assertThat(context.host()).isEqualTo("archive.local:8080");
        assertThat(context.forwarded()).isEqualTo("for=10.0.0.10;proto=https;host=archive.example");
        assertThat(context.xForwardedFor()).isEqualTo("10.0.0.10, 10.0.0.11");
        assertThat(context.xRealIp()).isEqualTo("10.0.0.10");
        assertThat(context.client().browserName()).isEqualTo("Chrome");
        assertThat(context.client().browserVersion()).isEqualTo("140");
        assertThat(context.client().osName()).isEqualTo("Windows NT");
        assertThat(context.client().deviceType()).isEqualTo("desktop");
    }
}
