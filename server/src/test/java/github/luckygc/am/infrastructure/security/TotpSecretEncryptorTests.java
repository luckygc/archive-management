package github.luckygc.am.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.infrastructure.security.config.TotpEncryptionProperties;

@DisplayName("TOTP AES-256-GCM 密钥保护")
class TotpSecretEncryptorTests {

    @Test
    @DisplayName("每次加密使用随机 nonce 且可以解密")
    void shouldUseRandomNonceAndDecrypt() {
        TotpSecretEncryptor encryptor = encryptor((byte) 1);

        String first = encryptor.encrypt("JBSWY3DPEHPK3PXP");
        String second = encryptor.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(encryptor.decrypt(second)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    @DisplayName("错误主密钥不能解密密文")
    void wrongKeyShouldFailClosed() {
        String encrypted = encryptor((byte) 1).encrypt("JBSWY3DPEHPK3PXP");

        assertThatThrownBy(() -> encryptor((byte) 2).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOTP 密钥解密失败");
    }

    @Test
    @DisplayName("缺少或长度错误的主密钥时拒绝加密")
    void missingOrInvalidKeyShouldFailClosed() {
        assertThatThrownBy(
                        () ->
                                new TotpSecretEncryptor(new TotpEncryptionProperties(null))
                                        .encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
        assertThatThrownBy(
                        () ->
                                new TotpSecretEncryptor(
                                                new TotpEncryptionProperties(
                                                        Base64.getEncoder()
                                                                .encodeToString(new byte[16])))
                                        .encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }

    private TotpSecretEncryptor encryptor(byte value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        return new TotpSecretEncryptor(
                new TotpEncryptionProperties(Base64.getEncoder().encodeToString(key)));
    }
}
