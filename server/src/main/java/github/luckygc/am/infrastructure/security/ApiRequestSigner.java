package github.luckygc.am.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import github.luckygc.am.common.exception.BadRequestException;

@Component
public class ApiRequestSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    public String sign(CanonicalRequest request, String secret) {
        return signInternal(request, secret);
    }

    private String signInternal(CanonicalRequest request, String secret) {
        validateSecret(secret);
        return BASE64_URL_ENCODER.encodeToString(
                hmacSha256(secret.getBytes(StandardCharsets.UTF_8), request.canonicalText()));
    }

    public void verify(CanonicalRequest request, String signature, String secret) {
        if (StringUtils.isBlank(signature)) {
            throw badRequest(ApiRequestSignatureFilter.SIGNATURE_HEADER, "请求签名不能为空");
        }
        byte[] expected = BASE64_URL_DECODER.decode(signInternal(request, secret));
        byte[] actual = decodeSignature(signature);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw badRequest(ApiRequestSignatureFilter.SIGNATURE_HEADER, "请求签名不匹配");
        }
    }

    private byte[] hmacSha256(byte[] secret, String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("请求签名计算失败", exception);
        }
    }

    private void validateSecret(String secret) {
        if (StringUtils.length(secret) < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "archive.security.request-signature.secret 至少需要 32 个字符");
        }
    }

    private byte[] decodeSignature(String signature) {
        try {
            return BASE64_URL_DECODER.decode(signature);
        } catch (IllegalArgumentException exception) {
            throw badRequest(ApiRequestSignatureFilter.SIGNATURE_HEADER, "请求签名编码不正确");
        }
    }

    private BadRequestException badRequest(String field, String description) {
        return new BadRequestException("请求签名不合法", field, description);
    }

    public record CanonicalRequest(
            String method,
            String path,
            String queryString,
            String timestamp,
            String nonce,
            String bodySha256) {

        String canonicalText() {
            return String.join(
                    "\n",
                    StringUtils.upperCase(method),
                    StringUtils.defaultString(path),
                    canonicalQuery(queryString),
                    StringUtils.defaultString(timestamp),
                    StringUtils.defaultString(nonce),
                    StringUtils.defaultString(bodySha256));
        }

        private static String canonicalQuery(String queryString) {
            if (StringUtils.isBlank(queryString)) {
                return "";
            }
            return String.join("&", Arrays.stream(queryString.split("&", -1)).sorted().toList());
        }
    }
}
