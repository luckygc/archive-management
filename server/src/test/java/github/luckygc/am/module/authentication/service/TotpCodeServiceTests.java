package github.luckygc.am.module.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RFC 6238 TOTP 验证")
class TotpCodeServiceTests {

    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final TotpCodeService service = new TotpCodeService();

    @Test
    @DisplayName("生成 RFC 6238 SHA1 六位验证码")
    void shouldGenerateRfc6238Code() {
        assertThat(service.generateCode(RFC_SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
    }

    @Test
    @DisplayName("只接受当前时间步前后一个窗口")
    void shouldOnlyAcceptAdjacentTimeSteps() {
        String code = service.generateCode(RFC_SECRET, Instant.ofEpochSecond(59));

        assertThat(service.findMatchingStep(RFC_SECRET, code, Instant.ofEpochSecond(89)))
                .hasValue(1L);
        assertThat(service.findMatchingStep(RFC_SECRET, code, Instant.ofEpochSecond(119)))
                .isEmpty();
    }

    @Test
    @DisplayName("生成的密钥具有 160 bit 随机熵并使用 Base32")
    void generatedSecretShouldHaveRequiredEntropy() {
        String secret = service.generateSecret();

        assertThat(secret).matches("[A-Z2-7]{32}");
    }

    @Test
    @DisplayName("provisioning URI 固定算法、位数和周期")
    void provisioningUriShouldDeclareParameters() {
        assertThat(service.provisioningUri("Archive Management", "张三", RFC_SECRET))
                .startsWith("otpauth://totp/Archive%20Management%3A%E5%BC%A0%E4%B8%89?")
                .contains("secret=" + RFC_SECRET)
                .contains("issuer=Archive%20Management")
                .endsWith("algorithm=SHA1&digits=6&period=30");
    }
}
