package github.luckygc.am.module.authentication.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;

import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authentication.AuthenticationTotpLoginChallenge;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.LoginBlockedException;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpCredentialDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpLoginChallengeDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;

@Service
public class TotpLoginChallengeService {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;

    private final AuthenticationTotpLoginChallengeDataRepository challengeRepository;
    private final AuthenticationTotpCredentialDataRepository credentialRepository;
    private final AuthenticationUserDataRepository userRepository;
    private final DatabaseUserDetailsService userDetailsService;
    private final TotpCredentialService credentialService;
    private final LoginFailureLimitService failureLimitService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpLoginChallengeService(
            AuthenticationTotpLoginChallengeDataRepository challengeRepository,
            AuthenticationTotpCredentialDataRepository credentialRepository,
            AuthenticationUserDataRepository userRepository,
            DatabaseUserDetailsService userDetailsService,
            TotpCredentialService credentialService,
            LoginFailureLimitService failureLimitService,
            Clock clock) {
        this.challengeRepository = challengeRepository;
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.credentialService = credentialService;
        this.failureLimitService = failureLimitService;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Throwable.class)
    public Optional<TotpLoginChallengeResponse> startIfRequired(Authentication authentication) {
        Long userId = AuthenticatedUsers.requireUserId(authentication.getPrincipal());
        if (credentialRepository.findById(userId).isEmpty()) {
            return Optional.empty();
        }
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String challengeToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = clock.instant().plus(CHALLENGE_TTL);
        AuthenticationTotpLoginChallenge challenge = new AuthenticationTotpLoginChallenge();
        challenge.setTokenKey(tokenKey(challengeToken));
        challenge.setUserId(userId);
        challenge.setFailedAttempts(0);
        challenge.setExpiresAt(localDateTime(expiresAt));
        challengeRepository.insert(challenge);
        return Optional.of(new TotpLoginChallengeResponse(challengeToken, expiresAt));
    }

    @Transactional(rollbackFor = Throwable.class)
    public VerificationResult verify(String challengeToken, String code) {
        String key = tokenKey(challengeToken);
        AuthenticationTotpLoginChallenge challenge = challengeRepository.findById(key).orElse(null);
        LocalDateTime now = localDateTime(clock.instant());
        if (challenge == null
                || !challenge.getExpiresAt().isAfter(now)
                || challenge.getFailedAttempts() >= MAX_ATTEMPTS) {
            if (challenge != null) {
                challengeRepository.deleteById(key);
            }
            return VerificationResult.challengeInvalid(null);
        }

        AuthenticationUser user = userRepository.findById(challenge.getUserId()).orElse(null);
        if (user == null
                || !user.isEnabled()
                || credentialRepository.findById(challenge.getUserId()).isEmpty()) {
            challengeRepository.deleteById(key);
            return VerificationResult.challengeInvalid(user == null ? null : user.getUsername());
        }
        try {
            failureLimitService.assertLoginAllowed(user.getUsername());
        } catch (LoginBlockedException ex) {
            challengeRepository.deleteById(key);
            return VerificationResult.challengeInvalid(user.getUsername());
        }

        if (!credentialService.verifyAndAdvance(user.getId(), code)) {
            challengeRepository.recordFailure(key, now, MAX_ATTEMPTS);
            failureLimitService.recordFailure(user.getUsername());
            if (challenge.getFailedAttempts() + 1 >= MAX_ATTEMPTS) {
                challengeRepository.deleteById(key);
                return VerificationResult.challengeInvalid(user.getUsername());
            }
            return VerificationResult.codeInvalid(user.getUsername());
        }

        if (challengeRepository.consume(key, now, MAX_ATTEMPTS) != 1) {
            throw new TotpChallengeConsumptionException();
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        userDetails, null, userDetails.getAuthorities());
        return VerificationResult.success(authentication);
    }

    private String tokenKey(String challengeToken) {
        return DigestUtils.sha256Hex(challengeToken == null ? "" : challengeToken);
    }

    private LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public record TotpLoginChallengeResponse(String challengeToken, Instant expiresAt) {}

    public record VerificationResult(
            @Nullable Authentication authentication, @Nullable String username, String errorCode) {

        public static VerificationResult success(Authentication authentication) {
            return new VerificationResult(authentication, authentication.getName(), "");
        }

        public static VerificationResult codeInvalid(String username) {
            return new VerificationResult(null, username, "TOTP_CODE_INVALID");
        }

        public static VerificationResult challengeInvalid(@Nullable String username) {
            return new VerificationResult(null, username, "TOTP_CHALLENGE_INVALID");
        }

        public boolean successful() {
            return authentication != null;
        }
    }

    public static final class TotpChallengeConsumptionException extends RuntimeException {

        private static final long serialVersionUID = -1L;
    }
}
