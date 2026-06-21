package github.luckygc.am.module.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PowChallengeService {

    private static final int DEFAULT_CHALLENGE_COUNT = 50;
    private static final int DEFAULT_CHALLENGE_SIZE = 32;
    private static final int DEFAULT_CHALLENGE_DIFFICULTY = 4;
    private static final long CHALLENGE_EXPIRES_MS = 600_000L;
    private static final long TOKEN_EXPIRES_MS = 20 * 60 * 1000L;

    private final JdbcClient jdbcClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public PowChallengeService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public CapChallengeResponse createChallenge() {
        cleanExpired();

        String token = randomHex(25);
        long expires = System.currentTimeMillis() + CHALLENGE_EXPIRES_MS;

        jdbcClient.sql("""
                        insert into am_auth_cap_challenge
                            (token, challenge_count, challenge_size, difficulty, expires_at)
                        values
                            (:token, :challengeCount, :challengeSize, :difficulty, :expiresAt)
                        """)
                .param("token", token)
                .param("challengeCount", DEFAULT_CHALLENGE_COUNT)
                .param("challengeSize", DEFAULT_CHALLENGE_SIZE)
                .param("difficulty", DEFAULT_CHALLENGE_DIFFICULTY)
                .param("expiresAt", toLocalDateTime(expires))
                .update();

        return new CapChallengeResponse(
                new CapChallenge(DEFAULT_CHALLENGE_COUNT, DEFAULT_CHALLENGE_SIZE, DEFAULT_CHALLENGE_DIFFICULTY),
                token,
                expires);
    }

    @Transactional
    public Map<String, Object> redeemChallenge(CapRedeemCommand command) {
        if (command == null || StringUtils.isBlank(command.token()) || command.solutions() == null
                || command.solutions().stream().anyMatch(solution -> solution == null)) {
            return redeemFailure("Invalid body");
        }

        cleanExpired();

        ChallengeRecord challenge = jdbcClient.sql("""
                        select token, challenge_count, challenge_size, difficulty, expires_at
                        from am_auth_cap_challenge
                        where token = :token
                        for update
                        """)
                .param("token", command.token())
                .query((rs, rowNum) -> new ChallengeRecord(
                        rs.getString("token"),
                        rs.getInt("challenge_count"),
                        rs.getInt("challenge_size"),
                        rs.getInt("difficulty"),
                        rs.getTimestamp("expires_at").toLocalDateTime()))
                .optional()
                .orElse(null);

        deleteChallenge(command.token());

        if (challenge == null || toEpochMillis(challenge.expiresAt()) < System.currentTimeMillis()) {
            return redeemFailure("Challenge invalid or expired");
        }

        if (!validSolutions(challenge, command.solutions())) {
            return redeemFailure("Invalid solution");
        }

        String vertoken = randomHex(15);
        long expires = System.currentTimeMillis() + TOKEN_EXPIRES_MS;
        String id = randomHex(8);
        String tokenKey = id + ":" + sha256Hex(vertoken);

        jdbcClient.sql("""
                        insert into am_auth_cap_token (token_key, expires_at)
                        values (:tokenKey, :expiresAt)
                        """)
                .param("tokenKey", tokenKey)
                .param("expiresAt", toLocalDateTime(expires))
                .update();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("token", id + ":" + vertoken);
        response.put("expires", expires);
        return response;
    }

    public Map<String, Object> validateToken(String token, boolean keepToken) {
        if (keepToken) {
            return validationResult(validToken(token));
        }

        try {
            consumeToken(token);
            return validationResult(true);
        } catch (PowChallengeException ex) {
            return validationResult(false);
        }
    }

    @Transactional
    public void consumeToken(String token) {
        String tokenKey = tokenKey(token);
        if (tokenKey == null) {
            throw new PowChallengeException("安全验证已失效，请重试");
        }

        int deleted = jdbcClient.sql("""
                        delete from am_auth_cap_token
                        where token_key = :tokenKey
                          and expires_at > localtimestamp
                        """)
                .param("tokenKey", tokenKey)
                .update();

        if (deleted != 1) {
            throw new PowChallengeException("安全验证已失效，请重试");
        }
    }

    private Map<String, Object> redeemFailure(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("message", message);
        return response;
    }

    private Map<String, Object> validationResult(boolean success) {
        return Map.of("success", success);
    }

    private boolean validToken(String token) {
        String tokenKey = tokenKey(token);
        if (tokenKey == null) {
            return false;
        }

        LocalDateTime expiresAt = jdbcClient.sql("""
                        select expires_at
                        from am_auth_cap_token
                        where token_key = :tokenKey
                        """)
                .param("tokenKey", tokenKey)
                .query((rs, rowNum) -> rs.getTimestamp("expires_at").toLocalDateTime())
                .optional()
                .orElse(null);

        return expiresAt != null && toEpochMillis(expiresAt) > System.currentTimeMillis();
    }

    private String tokenKey(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }

        String[] parts = token.split(":", -1);
        if (parts.length != 2 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])) {
            return null;
        }

        return parts[0] + ":" + sha256Hex(parts[1]);
    }

    private boolean validSolutions(ChallengeRecord challenge, List<Long> solutions) {
        if (solutions.size() != challenge.challengeCount()) {
            return false;
        }

        for (int index = 0; index < challenge.challengeCount(); index++) {
            String salt = prng(challenge.token() + (index + 1), challenge.challengeSize());
            String target = prng(challenge.token() + (index + 1) + "d", challenge.difficulty());
            if (!sha256Hex(salt + solutions.get(index)).startsWith(target)) {
                return false;
            }
        }

        return true;
    }

    private void deleteChallenge(String token) {
        jdbcClient.sql("delete from am_auth_cap_challenge where token = :token")
                .param("token", token)
                .update();
    }

    private void cleanExpired() {
        jdbcClient.sql("delete from am_auth_cap_challenge where expires_at < localtimestamp").update();
        jdbcClient.sql("delete from am_auth_cap_token where expires_at < localtimestamp").update();
    }

    private String randomHex(int bytesCount) {
        byte[] bytes = new byte[bytesCount];
        secureRandom.nextBytes(bytes);
        return Hex.encodeHexString(bytes);
    }

    private String sha256Hex(String value) {
        return DigestUtils.sha256Hex(value);
    }

    private String prng(String seed, int length) {
        long state = fnv1a(seed);
        StringBuilder result = new StringBuilder(length);
        while (result.length() < length) {
            state ^= (state << 13) & 0xffff_ffffL;
            state &= 0xffff_ffffL;
            state ^= state >>> 17;
            state &= 0xffff_ffffL;
            state ^= (state << 5) & 0xffff_ffffL;
            state &= 0xffff_ffffL;
            result.append("%08x".formatted(state));
        }
        return result.substring(0, length);
    }

    private long fnv1a(String value) {
        long hash = 2166136261L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash = (hash
                    + ((hash << 1) & 0xffff_ffffL)
                    + ((hash << 4) & 0xffff_ffffL)
                    + ((hash << 7) & 0xffff_ffffL)
                    + ((hash << 8) & 0xffff_ffffL)
                    + ((hash << 24) & 0xffff_ffffL)) & 0xffff_ffffL;
        }
        return hash;
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public record CapChallengeResponse(CapChallenge challenge, String token, long expires) {
    }

    public record CapChallenge(int c, int s, int d) {
    }

    public record CapRedeemCommand(String token, List<Long> solutions) {
    }

    public record CapValidateCommand(String token, Boolean keepToken) {
    }

    private record ChallengeRecord(
            String token,
            int challengeCount,
            int challengeSize,
            int difficulty,
            LocalDateTime expiresAt) {
    }
}
