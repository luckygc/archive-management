package github.luckygc.am.module.authentication;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.cleanup.ExpiredDataCleaner;
import github.luckygc.am.common.cleanup.ExpiredDataCleanupResult;
import github.luckygc.am.module.authentication.repository.LoginFailureLimitDataRepository;

@Component
public class LoginFailureLimitExpiredDataCleaner implements ExpiredDataCleaner {

    private final LoginFailureLimitDataRepository repository;

    public LoginFailureLimitExpiredDataCleaner(LoginFailureLimitDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "login_failure_limit";
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public ExpiredDataCleanupResult cleanupExpired(LocalDateTime now) {
        return new ExpiredDataCleanupResult(name(), repository.deleteExpired(now));
    }
}
