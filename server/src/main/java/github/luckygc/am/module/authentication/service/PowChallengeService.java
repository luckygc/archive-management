package github.luckygc.am.module.authentication.service;

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

import github.luckygc.am.module.authentication.AuthenticationCapChallenge;
import github.luckygc.am.module.authentication.AuthenticationCapToken;
import github.luckygc.am.module.authentication.PowChallengeException;
import github.luckygc.am.module.authentication.repository.AuthenticationCapChallengeDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationCapTokenDataRepository;

@Service
public class PowChallengeService {

    private static final int DEFAULT_CHALLENGE_COUNT = 50;
    private static final int DEFAULT_CHALLENGE_SIZE = 32;
    private static final int DEFAULT_CHALLENGE_DIFFICULTY = 4;
    private static final int MAX_CHALLENGE_DIFFICULTY = 6;
    private static final long CHALLENGE_EXPIRES_MS = 600_000L;
    private static final long TOKEN_EXPIRES_MS = 20 * 60 * 1000L;

    private final AuthenticationCapChallengeDataRepository challengeRepository;
    private final AuthenticationCapTokenDataRepository tokenRepository;
    private final LoginFailureLimitService failureLimitService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PowChallengeService(
            AuthenticationCapChallengeDataRepository challengeRepository,
            AuthenticationCapTokenDataRepository tokenRepository,
            LoginFailureLimitService failureLimitService) {
        this.challengeRepository = challengeRepository;
        this.tokenRepository = tokenRepository;
        this.failureLimitService = failureLimitService;
    }

    @Transactional
    public CapChallengeResponse createChallenge() {
        return createChallenge(null);
    }

    @Transactional
    public CapChallengeResponse createChallenge(@Nullable CapChallengeRequest request) {
        String token = randomHex(25);
        long expires = System.currentTimeMillis() + CHALLENGE_EXPIRES_MS;
        String username = request == null ? null : request.username();
        String usernameHash = usernameHash(username);
        int difficulty =
                request == null
                        ? DEFAULT_CHALLENGE_DIFFICULTY
                        : failureLimitService.difficulty(
                                username, DEFAULT_CHALLENGE_DIFFICULTY, MAX_CHALLENGE_DIFFICULTY);

        AuthenticationCapChallenge challenge = new AuthenticationCapChallenge();
        challenge.setToken(token);
        challenge.setChallengeCount(DEFAULT_CHALLENGE_COUNT);
        challenge.setChallengeSize(DEFAULT_CHALLENGE_SIZE);
        challenge.setDifficulty(difficulty);
        challenge.setUsernameHash(usernameHash);
        challenge.setExpiresAt(toLocalDateTime(expires));
        challengeRepository.insert(challenge);

        return new CapChallengeResponse(
                new CapChallenge(DEFAULT_CHALLENGE_COUNT, DEFAULT_CHALLENGE_SIZE, difficulty),
                token,
                expires);
    }

    @Transactional
    public Map<String, Object> redeemChallenge(@Nullable CapRedeemRequest request) {
        if (request == null
                || StringUtils.isBlank(request.token())
                || request.solutions() == null
                || request.solutions().stream().anyMatch(Objects::isNull)) {
            return redeemFailure("Invalid body");
        }

        AuthenticationCapChallenge challenge =
                challengeRepository.findById(request.token()).orElse(null);

        if (challenge == null
                || toEpochMillis(challenge.getExpiresAt()) < System.currentTimeMillis()) {
            return redeemFailure("Challenge invalid or expired");
        }

        if (!validSolutions(challenge, request.solutions())) {
            deleteChallenge(request.token());
            return redeemFailure("Invalid solution");
        }

        deleteChallenge(request.token());

        String vertoken = randomHex(15);
        long expires = System.currentTimeMillis() + TOKEN_EXPIRES_MS;
        String id = randomHex(8);
        String tokenKey = id + ":" + sha256Hex(vertoken);

        AuthenticationCapToken capToken = new AuthenticationCapToken();
        capToken.setTokenKey(tokenKey);
        capToken.setUsernameHash(challenge.getUsernameHash());
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
            consumeTokenInCurrentTransaction(token, null, false);
            return validationResult(true);
        } catch (PowChallengeException ex) {
            return validationResult(false);
        }
    }

    @Transactional
    public void consumeToken(@Nullable String token) {
        consumeTokenInCurrentTransaction(token, null, true);
    }

    @Transactional
    public void consumeToken(@Nullable String token, @Nullable String username) {
        consumeTokenInCurrentTransaction(token, username, true);
    }

    private void consumeTokenInCurrentTransaction(
            @Nullable String token, @Nullable String username, boolean requireUsernameBinding) {
        String tokenKey = tokenKey(token);
        if (tokenKey == null) {
            throw new PowChallengeException("安全验证已失效，请重试");
        }

        AuthenticationCapToken capToken = tokenRepository.findById(tokenKey).orElse(null);
        String expectedUsernameHash = usernameHash(username);
        if (capToken == null
                || toEpochMillis(capToken.getExpiresAt()) <= System.currentTimeMillis()) {
            throw new PowChallengeException("安全验证已失效，请重试");
        }
        if (requireUsernameBinding
                && (expectedUsernameHash == null
                        || !expectedUsernameHash.equals(capToken.getUsernameHash()))) {
            throw new PowChallengeException("安全验证已失效，请重试");
        }
        tokenRepository.deleteById(tokenKey);
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
                tokenRepository
                        .findById(tokenKey)
                        .map(AuthenticationCapToken::getExpiresAt)
                        .orElse(null);

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

    private boolean validSolutions(
            AuthenticationCapChallenge challenge, List<@Nullable Long> solutions) {
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

    private @Nullable String usernameHash(@Nullable String username) {
        String normalized = StringUtils.trimToNull(username);
        return normalized == null ? null : sha256Hex(normalized);
    }

    public record CapChallengeRequest(@Nullable String username) {}

    public record CapRedeemRequest(
            @Nullable String token, @Nullable List<@Nullable Long> solutions) {}

    public record CapValidateRequest(@Nullable String token, @Nullable Boolean keepToken) {}
}
