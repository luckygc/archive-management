package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.cleanup.ExpiredDataCleaner;
import github.luckygc.am.common.cleanup.ExpiredDataCleanupResult;
import github.luckygc.am.module.authentication.repository.AuthenticationCapChallengeDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationCapTokenDataRepository;

@Component
public class AuthenticationCapExpiredDataCleaner implements ExpiredDataCleaner {

    private static final String CLEANER_NAME = "auth_cap";

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
        return CLEANER_NAME;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public ExpiredDataCleanupResult cleanupExpired(LocalDateTime now) {
        int deleted = challengeRepository.deleteExpired(now) + tokenRepository.deleteExpired(now);
        return new ExpiredDataCleanupResult(CLEANER_NAME, deleted);
    }
}
