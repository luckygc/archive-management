package github.luckygc.am.module.authentication;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/** 访问令牌明文格式：{@code am1_<16位十六进制前缀>_<秘密>}。库中只保存前缀和 SHA-256。 */
public final class AccessTokenSecrets {

    public static final String SUGGESTED_READ_ONLY_SCOPE = "archive:item:read";
    private static final String PREFIX_MARK = "am1_";
    private static final int PREFIX_HEX_LENGTH = 16;
    private static final String DUMMY_HASH = "0".repeat(64);
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccessTokenSecrets() {}

    public static IssuedToken issue() {
        byte[] prefix = new byte[PREFIX_HEX_LENGTH / 2];
        byte[] secret = new byte[32];
        RANDOM.nextBytes(prefix);
        RANDOM.nextBytes(secret);
        String tokenPrefix = Hex.encodeHexString(prefix);
        String token =
                PREFIX_MARK
                        + tokenPrefix
                        + "_"
                        + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new IssuedToken(tokenPrefix, hash(token), token);
    }

    public static String hash(String token) {
        return DigestUtils.sha256Hex(token);
    }

    public static boolean matches(String token, @Nullable String storedHash) {
        String actual = hash(token);
        String expected = storedHash == null ? DUMMY_HASH : storedHash;
        return MessageDigest.isEqual(
                        actual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                && storedHash != null;
    }

    public static @Nullable String prefixOf(String token) {
        if (!token.startsWith(PREFIX_MARK)) {
            return null;
        }
        String rest = token.substring(PREFIX_MARK.length());
        int separator = rest.indexOf('_');
        if (separator != PREFIX_HEX_LENGTH) {
            return null;
        }
        String prefix = rest.substring(0, PREFIX_HEX_LENGTH).toLowerCase(Locale.ROOT);
        if (!prefix.chars().allMatch(AccessTokenSecrets::isHex)) {
            return null;
        }
        if (StringUtils.isBlank(rest.substring(separator + 1))) {
            return null;
        }
        return prefix;
    }

    public static String encodeScopes(Collection<String> scopes) {
        return scopes.stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .collect(Collectors.joining(","));
    }

    public static Set<String> decodeScopes(@Nullable String stored) {
        if (StringUtils.isBlank(stored)) {
            return Set.of();
        }
        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isHex(int character) {
        return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f');
    }

    public record IssuedToken(String prefix, String hash, String secret) {}
}
