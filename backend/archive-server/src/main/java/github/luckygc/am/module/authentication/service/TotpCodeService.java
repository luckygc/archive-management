package github.luckygc.am.module.authentication.service;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

@Service
public class TotpCodeService {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int SECRET_BYTES = 20;
    private static final long TIME_STEP_SECONDS = 30L;
    private static final int WINDOW = 1;
    private static final int CODE_MODULUS = 1_000_000;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base32 base32 = new Base32();

    public String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return base32.encodeAsString(secret).replace("=", "").toUpperCase(Locale.ROOT);
    }

    public OptionalLong findMatchingStep(String secret, String code, Instant now) {
        if (code == null || !code.matches("[0-9]{6}")) {
            return OptionalLong.empty();
        }
        long currentStep = now.getEpochSecond() / TIME_STEP_SECONDS;
        for (long step = currentStep + WINDOW; step >= currentStep - WINDOW; step--) {
            if (generateCode(secret, step).equals(code)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public String generateCode(String secret, Instant instant) {
        return generateCode(secret, instant.getEpochSecond() / TIME_STEP_SECONDS);
    }

    public String provisioningUri(String issuer, String username, String secret) {
        String label = encode(issuer + ":" + username);
        return "otpauth://totp/"
                + label
                + "?secret="
                + encode(secret)
                + "&issuer="
                + encode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private String generateCode(String secret, long step) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(base32.decode(secret), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary =
                    ((digest[offset] & 0x7f) << 24)
                            | ((digest[offset + 1] & 0xff) << 16)
                            | ((digest[offset + 2] & 0xff) << 8)
                            | (digest[offset + 3] & 0xff);
            return "%06d".formatted(binary % CODE_MODULUS);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP 验证器初始化失败", ex);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
