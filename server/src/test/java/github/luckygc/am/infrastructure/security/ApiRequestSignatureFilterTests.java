package github.luckygc.am.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import github.luckygc.am.infrastructure.security.ApiRequestSigner.CanonicalRequest;
import github.luckygc.am.infrastructure.security.config.ApiRequestSignatureProperties;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("接口请求 HMAC 签名过滤器")
class ApiRequestSignatureFilterTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-06-25T10:20:30Z");

    private final ApiRequestSigner signer = new ApiRequestSigner();

    @Test
    @DisplayName("签名正确时放行并保留请求体")
    void validSignatureAllowsRequestAndKeepsBodyReadable() throws Exception {
        ApiRequestSignatureFilter filter = filter(true);
        MockHttpServletRequest request = request("{\"title\":\"A\"}");
        sign(request, "categoryId=1&fondsCode=A", DigestUtils.sha256Hex("{\"title\":\"A\"}"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> body = new AtomicReference<>();
        FilterChain chain =
                (servletRequest, servletResponse) ->
                        body.set(
                                new String(
                                        servletRequest.getInputStream().readAllBytes(),
                                        StandardCharsets.UTF_8));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.get()).isEqualTo("{\"title\":\"A\"}");
    }

    @Test
    @DisplayName("参数被篡改时返回 ProblemDetail 错误")
    void tamperedQueryIsRejected() throws Exception {
        ApiRequestSignatureFilter filter = filter(true);
        MockHttpServletRequest request = request("{\"title\":\"A\"}");
        sign(request, "categoryId=1&fondsCode=A", DigestUtils.sha256Hex("{\"title\":\"A\"}"));
        request.setQueryString("categoryId=2&fondsCode=A");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("INVALID_ARGUMENT");
        assertThat(response.getContentAsString()).contains("fieldViolations");
    }

    @Test
    @DisplayName("未启用签名时不校验请求头")
    void disabledSignatureSkipsVerification() throws Exception {
        ApiRequestSignatureFilter filter = filter(false);
        MockHttpServletRequest request = request("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> invoked.set(true));

        assertThat(invoked.get()).isTrue();
    }

    @Test
    @DisplayName("登录前置接口不校验请求签名")
    void loginBootstrapRequestSkipsVerification() throws Exception {
        ApiRequestSignatureFilter filter = filter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth:login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> invoked.set(true));

        assertThat(invoked.get()).isTrue();
    }

    private ApiRequestSignatureFilter filter(boolean enabled) {
        ApiRequestSignatureProperties properties = new ApiRequestSignatureProperties();
        properties.setEnabled(enabled);
        properties.setSecret(SECRET);
        properties.setClockSkew(Duration.ofMinutes(5));
        return new ApiRequestSignatureFilter(
                properties,
                signer,
                JsonMapper.builder().findAndAddModules().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MockHttpServletRequest request(String body) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-records");
        request.setQueryString("categoryId=1&fondsCode=A");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        return request;
    }

    private void sign(MockHttpServletRequest request, String queryString, String bodySha256) {
        String timestamp = NOW.toString();
        String nonce = "nonce-1";
        String signature =
                signer.sign(
                        new CanonicalRequest(
                                request.getMethod(),
                                request.getRequestURI(),
                                queryString,
                                timestamp,
                                nonce,
                                bodySha256),
                        SECRET);
        request.addHeader(ApiRequestSignatureFilter.TIMESTAMP_HEADER, timestamp);
        request.addHeader(ApiRequestSignatureFilter.NONCE_HEADER, nonce);
        request.addHeader(ApiRequestSignatureFilter.SIGNATURE_HEADER, signature);
    }
}
