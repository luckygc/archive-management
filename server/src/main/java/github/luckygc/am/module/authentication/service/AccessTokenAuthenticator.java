package github.luckygc.am.module.authentication.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.security.AccessTokenGrant;
import github.luckygc.am.common.security.AccessTokenSubjectType;
import github.luckygc.am.common.security.MachineAccessPrincipal;
import github.luckygc.am.module.authentication.AccessToken;
import github.luckygc.am.module.authentication.AccessTokenFailureReason;
import github.luckygc.am.module.authentication.AccessTokenSecrets;
import github.luckygc.am.module.authentication.AccessTokenType;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.BearerAccessAuthentication;
import github.luckygc.am.module.authentication.ServiceAccount;
import github.luckygc.am.module.authentication.ServiceAccountStatus;
import github.luckygc.am.module.authentication.repository.AccessTokenDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authentication.repository.ServiceAccountDataRepository;

@Service
public class AccessTokenAuthenticator {

    private final AccessTokenDataRepository tokenRepository;
    private final ServiceAccountDataRepository serviceAccountRepository;
    private final AuthenticationUserDataRepository userRepository;
    private final DatabaseUserDetailsService userDetailsService;
    private final Clock clock;

    public AccessTokenAuthenticator(
            AccessTokenDataRepository tokenRepository,
            ServiceAccountDataRepository serviceAccountRepository,
            AuthenticationUserDataRepository userRepository,
            DatabaseUserDetailsService userDetailsService,
            Clock clock) {
        this.tokenRepository = tokenRepository;
        this.serviceAccountRepository = serviceAccountRepository;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthenticationResult authenticate(String rawToken) {
        String prefix = AccessTokenSecrets.prefixOf(rawToken);
        if (prefix == null) {
            return AuthenticationResult.rejected(AccessTokenFailureReason.INVALID, null, null);
        }
        AccessToken token = tokenRepository.findByTokenPrefix(prefix);
        if (token == null || !AccessTokenSecrets.matches(rawToken, token.getTokenHash())) {
            return AuthenticationResult.rejected(AccessTokenFailureReason.INVALID, null, prefix);
        }
        if (token.getRevokedAt() != null) {
            return rejected(token, AccessTokenFailureReason.REVOKED);
        }
        if (!token.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            return rejected(token, AccessTokenFailureReason.EXPIRED);
        }
        if (token.getTokenType() == AccessTokenType.SERVICE_ACCOUNT) {
            return authenticateServiceAccount(token);
        }
        return authenticateUser(token);
    }

    private AuthenticationResult authenticateServiceAccount(AccessToken token) {
        Long serviceAccountId = token.getServiceAccountId();
        if (serviceAccountId == null) {
            return rejected(token, AccessTokenFailureReason.DISABLED);
        }
        ServiceAccount account = serviceAccountRepository.findById(serviceAccountId).orElse(null);
        if (account == null || account.getStatus() != ServiceAccountStatus.ACTIVE) {
            return rejected(token, AccessTokenFailureReason.DISABLED);
        }
        Set<String> scopes = AccessTokenSecrets.decodeScopes(token.getScopes());
        AccessTokenGrant grant =
                new AccessTokenGrant(
                        token.getId(),
                        AccessTokenSubjectType.SERVICE_ACCOUNT,
                        account.getId(),
                        null,
                        scopes);
        BearerAccessAuthentication authentication =
                new BearerAccessAuthentication(
                        new MachineAccessPrincipal(account.getId(), account.getName()));
        return AuthenticationResult.accepted(authentication, grant, token);
    }

    private AuthenticationResult authenticateUser(AccessToken token) {
        Long userId = token.getUserId();
        if (userId == null) {
            return rejected(token, AccessTokenFailureReason.DISABLED);
        }
        AuthenticationUser user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isEnabled()) {
            return rejected(token, AccessTokenFailureReason.DISABLED);
        }
        Set<String> scopes = AccessTokenSecrets.decodeScopes(token.getScopes());
        AccessTokenGrant grant =
                new AccessTokenGrant(
                        token.getId(),
                        AccessTokenSubjectType.USER,
                        user.getId(),
                        user.getId(),
                        scopes);
        BearerAccessAuthentication authentication =
                new BearerAccessAuthentication(
                        userDetailsService.loadUserByUsername(user.getUsername()));
        return AuthenticationResult.accepted(authentication, grant, token);
    }

    private AuthenticationResult rejected(AccessToken token, AccessTokenFailureReason reason) {
        return AuthenticationResult.rejected(reason, token, token.getTokenPrefix());
    }

    public record AuthenticationResult(
            boolean accepted,
            @Nullable BearerAccessAuthentication authentication,
            @Nullable AccessTokenGrant grant,
            @Nullable AccessTokenFailureReason failureReason,
            @Nullable Long tokenId,
            @Nullable String tokenPrefix,
            @Nullable Long serviceAccountId,
            @Nullable Long representedUserId,
            String callerType) {

        static AuthenticationResult accepted(
                BearerAccessAuthentication authentication,
                AccessTokenGrant grant,
                AccessToken token) {
            return new AuthenticationResult(
                    true,
                    authentication,
                    grant,
                    null,
                    token.getId(),
                    token.getTokenPrefix(),
                    token.getServiceAccountId(),
                    token.getUserId(),
                    callerType(token));
        }

        static AuthenticationResult rejected(
                AccessTokenFailureReason reason,
                @Nullable AccessToken token,
                @Nullable String prefix) {
            if (token == null) {
                return new AuthenticationResult(
                        false, null, null, reason, null, prefix, null, null, "UNKNOWN");
            }
            return new AuthenticationResult(
                    false,
                    null,
                    null,
                    reason,
                    token.getId(),
                    prefix,
                    token.getServiceAccountId(),
                    token.getUserId(),
                    callerType(token));
        }

        private static String callerType(AccessToken token) {
            return token.getTokenType() == AccessTokenType.SERVICE_ACCOUNT
                    ? "SERVICE_ACCOUNT"
                    : "USER";
        }
    }
}
