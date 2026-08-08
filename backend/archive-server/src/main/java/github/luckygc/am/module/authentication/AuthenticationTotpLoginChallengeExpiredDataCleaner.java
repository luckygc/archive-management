package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.cleanup.ExpiredDataCleaner;
import github.luckygc.am.common.cleanup.ExpiredDataCleanupResult;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpEnrollmentDataRepository;
import github.luckygc.am.module.authentication.repository.AuthenticationTotpLoginChallengeDataRepository;

@Component
public class AuthenticationTotpLoginChallengeExpiredDataCleaner implements ExpiredDataCleaner {

    private static final String CLEANER_NAME = "auth_totp_login_challenge";

    private final AuthenticationTotpLoginChallengeDataRepository repository;
    private final AuthenticationTotpEnrollmentDataRepository enrollmentRepository;

    public AuthenticationTotpLoginChallengeExpiredDataCleaner(
            AuthenticationTotpLoginChallengeDataRepository repository,
            AuthenticationTotpEnrollmentDataRepository enrollmentRepository) {
        this.repository = repository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    public String name() {
        return CLEANER_NAME;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public ExpiredDataCleanupResult cleanupExpired(LocalDateTime now) {
        int deleted = repository.deleteExpired(now) + enrollmentRepository.deleteExpired(now);
        return new ExpiredDataCleanupResult(CLEANER_NAME, deleted);
    }
}
