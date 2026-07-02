package github.luckygc.am.module.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.module.authentication.LoginBlockedException;
import github.luckygc.am.module.authentication.LoginFailureLimit;
import github.luckygc.am.module.authentication.LoginFailureLimitProperties;
import github.luckygc.am.module.authentication.repository.LoginFailureLimitDataRepository;

@DisplayName("登录失败限制服务")
class LoginFailureLimitServiceTests {

    @Test
    @DisplayName("失败达到阈值时锁定登录名")
    void recordFailureShouldLockUsernameWhenThresholdReached() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        LoginFailureLimit limit = limit(1, 0, LocalDateTime.now().minusMinutes(1), null);
        when(repository.findById("admin")).thenReturn(Optional.of(limit));
        LoginFailureLimitService service = service(repository);

        service.recordFailure("admin");

        assertThat(limit.getFailureCount()).isEqualTo(2);
        assertThat(limit.getLockLevel()).isEqualTo(1);
        assertThat(limit.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(4));
        verify(repository).update(limit);
    }

    @Test
    @DisplayName("锁定时长按锁定次数指数递增并封顶半小时")
    void recordFailureShouldIncreaseLockDurationWithThirtyMinutesCap() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        LoginFailureLimit limit = limit(1, 3, LocalDateTime.now().minusMinutes(1), null);
        when(repository.findById("admin")).thenReturn(Optional.of(limit));
        LoginFailureLimitService service = service(repository);

        service.recordFailure("admin");

        assertThat(limit.getLockLevel()).isEqualTo(4);
        assertThat(limit.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(29));
        assertThat(limit.getLockedUntil()).isBefore(LocalDateTime.now().plusMinutes(31));
    }

    @Test
    @DisplayName("锁定未过期时拒绝登录")
    void assertLoginAllowedShouldRejectLockedUsername() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        LoginFailureLimit limit =
                limit(
                        2,
                        1,
                        LocalDateTime.now().minusMinutes(1),
                        LocalDateTime.now().plusMinutes(5));
        when(repository.findById("admin")).thenReturn(Optional.of(limit));
        LoginFailureLimitService service = service(repository);

        assertThatThrownBy(() -> service.assertLoginAllowed("admin"))
                .isInstanceOf(LoginBlockedException.class);
    }

    @Test
    @DisplayName("锁定过期后的失败重新开始计数并保留锁定级别")
    void recordFailureShouldStartNewWindowAfterLockExpired() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        LoginFailureLimit limit =
                limit(
                        2,
                        1,
                        LocalDateTime.now().minusMinutes(6),
                        LocalDateTime.now().minusMinutes(1));
        when(repository.findById("admin")).thenReturn(Optional.of(limit));
        LoginFailureLimitService service = service(repository);

        service.recordFailure("admin");

        assertThat(limit.getFailureCount()).isEqualTo(1);
        assertThat(limit.getLockLevel()).isEqualTo(1);
        assertThat(limit.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("管理员重置删除登录失败状态")
    void resetShouldDeleteFailureLimitState() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        when(repository.findById("admin"))
                .thenReturn(Optional.of(limit(2, 1, LocalDateTime.now(), null)));
        LoginFailureLimitService service = service(repository);

        service.clear("admin");

        verify(repository).deleteById("admin");
    }

    @Test
    @DisplayName("首次失败写入未锁定状态")
    void firstFailureShouldInsertUnlockedState() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        when(repository.findById("admin")).thenReturn(Optional.empty());
        LoginFailureLimitService service = service(repository);

        service.recordFailure(" admin ");

        ArgumentCaptor<LoginFailureLimit> captor = ArgumentCaptor.forClass(LoginFailureLimit.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getFailureCount()).isEqualTo(1);
        assertThat(captor.getValue().getLockLevel()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    private static LoginFailureLimitService service(LoginFailureLimitDataRepository repository) {
        return new LoginFailureLimitService(
                repository,
                new LoginFailureLimitProperties(
                        Duration.ofMinutes(10),
                        2,
                        Duration.ofMinutes(5),
                        3,
                        Duration.ofHours(24),
                        Duration.ofHours(24)));
    }

    private static LoginFailureLimit limit(
            int failureCount,
            int lockLevel,
            LocalDateTime firstFailedAt,
            LocalDateTime lockedUntil) {
        LoginFailureLimit limit = new LoginFailureLimit();
        limit.setUsername("admin");
        limit.setFailureCount(failureCount);
        limit.setLockLevel(lockLevel);
        limit.setFirstFailedAt(firstFailedAt);
        limit.setLastFailedAt(firstFailedAt);
        limit.setLockedUntil(lockedUntil);
        limit.setCleanupAfter(firstFailedAt.plusDays(1));
        return limit;
    }
}
