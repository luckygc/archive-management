package github.luckygc.am.module.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PowChallengeService {

    private static final int DEFAULT_CHALLENGE_COUNT = 50;
    private static final int DEFAULT_CHALLENGE_SIZE = 32;
    private static final int DEFAULT_CHALLENGE_DIFFICULTY = 4;
    private static final long CHALLENGE_EXPIRES_MS = 600_000L;
    private static final long TOKEN_EXPIRES_MS = 20 * 60 * 1000L;

    private final AuthCapChallengeDataRepository challengeRepository;
    private final AuthCapTokenDataRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public PowChallengeService(
            AuthCapChallengeDataRepository challengeRepository,
            AuthCapTokenDataRepository tokenRepository) {
        this.challengeRepository = challengeRepository;
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public CapChallengeResponse createChallenge() {
        cleanExpired();

        String token = randomHex(25);
        long expires = System.currentTimeMillis() + CHALLENGE_EXPIRES_MS;

        AuthCapChallenge challenge = new AuthCapChallenge();
        challenge.setToken(token);
        challenge.setChallengeCount(DEFAULT_CHALLENGE_COUNT);
        challenge.setChallengeSize(DEFAULT_CHALLENGE_SIZE);
        challenge.setDifficulty(DEFAULT_CHALLENGE_DIFFICULTY);
        challenge.setExpiresAt(toLocalDateTime(expires));
        challengeRepository.insert(challenge);

        return new CapChallengeResponse(
                new CapChallenge(
                        DEFAULT_CHALLENGE_COUNT,
                        DEFAULT_CHALLENGE_SIZE,
                        DEFAULT_CHALLENGE_DIFFICULTY),
                token,
                expires);
    }

    @Transactional
    public Map<String, Object> redeemChallenge(@Nullable CapRedeemCommand command) {
        if (command == null
                || StringUtils.isBlank(command.token())
                || command.solutions() == null
                || command.solutions().stream().anyMatch(Objects::isNull)) {
            return redeemFailure("Invalid body");
        }

        cleanExpired();

        AuthCapChallenge challenge = challengeRepository.findById(command.token()).orElse(null);

        if (challenge == null
                || toEpochMillis(challenge.getExpiresAt()) < System.currentTimeMillis()) {
            return redeemFailure("Challenge invalid or expired");
        }

        if (!validSolutions(challenge, command.solutions())) {
            deleteChallenge(command.token());
            return redeemFailure("Invalid solution");
        }

        deleteChallenge(command.token());

        String vertoken = randomHex(15);
        long expires = System.currentTimeMillis() + TOKEN_EXPIRES_MS;
        String id = randomHex(8);
        String tokenKey = id + ":" + sha256Hex(vertoken);

        AuthCapToken capToken = new AuthCapToken();
        capToken.setTokenKey(tokenKey);
        capToken.setExpiresAt(toLocalDateTime(expires));
        tokenRepository.insert(capToken);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("token", id + ":" + vertoken);
        response.put("expires", expires);
        return response;
    }

    @Transactional
    public Map<String, Object> validateToken(@Nullable String token, boolean keepToken) {
        if (keepToken) {
            return validationResult(validToken(token));
        }

        try {
            consumeTokenInCurrentTransaction(token);
            return validationResult(true);
        } catch (PowChallengeException ex) {
            return validationResult(false);
        }
    }

    @Transactional
    public void consumeToken(@Nullable String token) {
        consumeTokenInCurrentTransaction(token);
    }

    private void consumeTokenInCurrentTransaction(@Nullable String token) {
        String tokenKey = tokenKey(token);
        if (tokenKey == null) {
            throw new PowChallengeException("安全验证已失效，请重试");
        }

        int deleted = tokenRepository.consume(tokenKey, LocalDateTime.now());

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

    private boolean validToken(@Nullable String token) {
        String tokenKey = tokenKey(token);
        if (tokenKey == null) {
            return false;
        }

        LocalDateTime expiresAt =
                tokenRepository.findById(tokenKey).map(AuthCapToken::getExpiresAt).orElse(null);

        return expiresAt != null && toEpochMillis(expiresAt) > System.currentTimeMillis();
    }

    private @Nullable String tokenKey(@Nullable String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }

        String[] parts = token.split(":", -1);
        if (parts.length != 2 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])) {
            return null;
        }

        return parts[0] + ":" + sha256Hex(parts[1]);
    }

    private boolean validSolutions(AuthCapChallenge challenge, List<@Nullable Long> solutions) {
        if (solutions.size() != challenge.getChallengeCount()) {
            return false;
        }

        for (int index = 0; index < challenge.getChallengeCount(); index++) {
            String salt = prng(challenge.getToken() + (index + 1), challenge.getChallengeSize());
            String target =
                    prng(challenge.getToken() + (index + 1) + "d", challenge.getDifficulty());
            Long solution = solutions.get(index);
            if (solution == null || !sha256Hex(salt + solution).startsWith(target)) {
                return false;
            }
        }

        return true;
    }

    private void deleteChallenge(String token) {
        challengeRepository.deleteById(token);
    }

    private void cleanExpired() {
        LocalDateTime now = LocalDateTime.now();
        challengeRepository.deleteExpired(now);
        tokenRepository.deleteExpired(now);
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
            hash =
                    (hash
                                    + ((hash << 1) & 0xffff_ffffL)
                                    + ((hash << 4) & 0xffff_ffffL)
                                    + ((hash << 7) & 0xffff_ffffL)
                                    + ((hash << 8) & 0xffff_ffffL)
                                    + ((hash << 24) & 0xffff_ffffL))
                            & 0xffff_ffffL;
        }
        return hash;
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public record CapChallengeResponse(CapChallenge challenge, String token, long expires) {}

    public record CapChallenge(int c, int s, int d) {}

    public record CapRedeemCommand(
            @Nullable String token, @Nullable List<@Nullable Long> solutions) {}

    public record CapValidateCommand(@Nullable String token, @Nullable Boolean keepToken) {}
}
