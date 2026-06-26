package github.luckygc.am.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.infrastructure.security.ApiRequestSigner.CanonicalRequest;

@DisplayName("接口请求 HMAC 签名")
class ApiRequestSignerTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final ApiRequestSigner signer = new ApiRequestSigner();

    @Test
    @DisplayName("同一请求可以生成并校验签名")
    void signAndVerify() {
        CanonicalRequest request =
                new CanonicalRequest(
                        "get",
                        "/api/v1/archive-records",
                        "fondsCode=A&categoryId=1",
                        "2026-06-25T10:20:30Z",
                        "nonce-1",
                        DigestUtils.sha256Hex(""));

        String signature = signer.sign(request, SECRET);

        assertThat(signature).isNotBlank();
        signer.verify(request, signature, SECRET);
    }

    @Test
    @DisplayName("查询参数顺序不影响签名")
    void queryOrderDoesNotAffectSignature() {
        CanonicalRequest first =
                new CanonicalRequest(
                        "GET",
                        "/api/v1/archive-records",
                        "fondsCode=A&categoryId=1",
                        "2026-06-25T10:20:30Z",
                        "nonce-1",
                        DigestUtils.sha256Hex(""));
        CanonicalRequest second =
                new CanonicalRequest(
                        "GET",
                        "/api/v1/archive-records",
                        "categoryId=1&fondsCode=A",
                        "2026-06-25T10:20:30Z",
                        "nonce-1",
                        DigestUtils.sha256Hex(""));

        assertThat(signer.sign(first, SECRET)).isEqualTo(signer.sign(second, SECRET));
    }

    @Test
    @DisplayName("请求参数被篡改时校验失败")
    void tamperedRequestIsRejected() {
        CanonicalRequest request =
                new CanonicalRequest(
                        "GET",
                        "/api/v1/archive-records",
                        "categoryId=1",
                        "2026-06-25T10:20:30Z",
                        "nonce-1",
                        DigestUtils.sha256Hex(""));
        String signature = signer.sign(request, SECRET);
        CanonicalRequest tampered =
                new CanonicalRequest(
                        "GET",
                        "/api/v1/archive-records",
                        "categoryId=2",
                        "2026-06-25T10:20:30Z",
                        "nonce-1",
                        DigestUtils.sha256Hex(""));

        assertThatThrownBy(() -> signer.verify(tampered, signature, SECRET))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("请求签名不合法");
    }

    @Test
    @DisplayName("请求体被篡改时校验失败")
    void tamperedBodyIsRejected() {
        CanonicalRequest request =
                new CanonicalRequest(
                        "POST",
                        "/api/v1/archive-records",
                        "",
                        "2026-06-25T10:20:30Z",
                        "nonce-1",
                        DigestUtils.sha256Hex("{\"title\":\"A\"}"));
        String signature = signer.sign(request, SECRET);
        CanonicalRequest tampered =
                new CanonicalRequest(
                        "POST",
                        "/api/v1/archive-records",
                        "",
                        "2026-06-25T10:20:30Z",
                        "nonce-1",
                        DigestUtils.sha256Hex("{\"title\":\"B\"}"));

        assertThatThrownBy(() -> signer.verify(tampered, signature, SECRET))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("请求签名不合法");
    }
}
