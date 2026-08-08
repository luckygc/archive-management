package github.luckygc.am.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import github.luckygc.am.infrastructure.security.config.TotpEncryptionProperties;

@Component
@Qualifier("totpTextEncryptor") public class TotpSecretEncryptor implements TextEncryptor {

    private static final String VERSION_PREFIX = "v1:";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] ASSOCIATED_DATA =
            "archive-management:totp:v1".getBytes(StandardCharsets.UTF_8);

    private final TotpEncryptionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpSecretEncryptor(TotpEncryptionProperties properties) {
        this.properties = properties;
    }

    @Override
    public String encrypt(String text) {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return VERSION_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP 密钥加密失败", ex);
        }
    }

    @Override
    public String decrypt(String encryptedText) {
        if (!StringUtils.startsWith(encryptedText, VERSION_PREFIX)) {
            throw new IllegalStateException("TOTP 密文版本不受支持");
        }
        try {
            byte[] payload =
                    Base64.getUrlDecoder().decode(encryptedText.substring(VERSION_PREFIX.length()));
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalStateException("TOTP 密文不完整");
            }
            byte[] nonce = Arrays.copyOf(payload, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, nonce);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP 密钥解密失败", ex);
        }
    }

    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(
                mode,
                new SecretKeySpec(encryptionKey(), "AES"),
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(ASSOCIATED_DATA);
        return cipher;
    }

    private byte[] encryptionKey() {
        String configured = StringUtils.trimToNull(properties.encryptionKey());
        if (configured == null) {
            throw new IllegalStateException("archive.authentication.totp.encryption-key 未配置");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(configured);
            if (decoded.length != KEY_BYTES) {
                throw new IllegalStateException(
                        "archive.authentication.totp.encryption-key 必须是 Base64 编码的 32 字节密钥");
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "archive.authentication.totp.encryption-key 必须是 Base64 编码的 32 字节密钥", ex);
        }
    }
}
