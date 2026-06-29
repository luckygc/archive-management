package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.cleanup.ExpiredDataCleaner;
import github.luckygc.am.common.cleanup.ExpiredDataCleanupResult;

@Component
public class AuthenticationCapExpiredDataCleaner implements ExpiredDataCleaner {

    private final AuthenticationCapChallengeDataRepository challengeRepository;
    private final AuthenticationCapTokenDataRepository tokenRepository;

    public AuthenticationCapExpiredDataCleaner(
            AuthenticationCapChallengeDataRepository challengeRepository,
            AuthenticationCapTokenDataRepository tokenRepository) {
        this.challengeRepository = challengeRepository;
        this.tokenRepository = tokenRepository;
    }

    @Override
    public String name() {
        return "auth_cap";
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public ExpiredDataCleanupResult cleanupExpired(LocalDateTime now) {
        int deleted = challengeRepository.deleteExpired(now) + tokenRepository.deleteExpired(now);
        return new ExpiredDataCleanupResult(name(), deleted);
    }
}
