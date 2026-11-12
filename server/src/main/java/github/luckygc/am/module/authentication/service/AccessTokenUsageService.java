package github.luckygc.am.module.authentication.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authentication.AccessToken;
import github.luckygc.am.module.authentication.repository.AccessTokenDataRepository;

@Service
public class AccessTokenUsageService {

    private final AccessTokenDataRepository tokenRepository;
    private final Clock clock;

    public AccessTokenUsageService(AccessTokenDataRepository tokenRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUsed(Long tokenId) {
        AccessToken token = tokenRepository.findById(tokenId).orElse(null);
        if (token == null) {
            return;
        }
        token.setLastUsedAt(LocalDateTime.now(clock));
        tokenRepository.update(token);
    }

    @Transactional
    public void revokeActiveForServiceAccount(Long serviceAccountId) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (AccessToken token : tokenRepository.findActiveByServiceAccountId(serviceAccountId)) {
            token.setRevokedAt(now);
            tokenRepository.update(token);
        }
    }
}
