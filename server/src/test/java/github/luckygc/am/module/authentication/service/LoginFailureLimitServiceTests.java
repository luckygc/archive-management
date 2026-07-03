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

import github.luckygc.am.module.authentication.LoginBlockedException;
import github.luckygc.am.module.authentication.LoginFailureLimit;
import github.luckygc.am.module.authentication.LoginFailureLimitProperties;
import github.luckygc.am.module.authentication.mapper.LoginFailureLimitMapper;
import github.luckygc.am.module.authentication.repository.LoginFailureLimitDataRepository;

@DisplayName("登录失败限制服务")
class LoginFailureLimitServiceTests {

    @Test
    @DisplayName("失败达到阈值时锁定登录名")
    void recordFailureShouldLockUsernameWhenThresholdReached() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        LoginFailureLimitMapper mapper = mock(LoginFailureLimitMapper.class);
        LoginFailureLimit limit = limit(1, 0, LocalDateTime.now().minusMinutes(1), null);
        when(mapper.insertFirstFailureIfAbsent(
                        org.mockito.Mockito.eq("admin"),
                        org.mockito.Mockito.any(),
                        org.mockito.Mockito.any()))
                .thenReturn(0);
        when(mapper.findByUsernameForUpdate("admin")).thenReturn(limit);
        LoginFailureLimitService service = service(repository, mapper);

        service.recordFailure("admin");

        assertThat(limit.getFailureCount()).isEqualTo(2);
        assertThat(limit.getLockLevel()).isEqualTo(1);
        assertThat(limit.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(4));
        verify(mapper).findByUsernameForUpdate("admin");
        verify(mapper).update(limit);
    }

    @Test
    @DisplayName("锁定时长按锁定次数指数递增并封顶半小时")
    void recordFailureShouldIncreaseLockDurationWithThirtyMinutesCap() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        LoginFailureLimitMapper mapper = mock(LoginFailureLimitMapper.class);
        LoginFailureLimit limit = limit(1, 3, LocalDateTime.now().minusMinutes(1), null);
        when(mapper.insertFirstFailureIfAbsent(
                        org.mockito.Mockito.eq("admin"),
                        org.mockito.Mockito.any(),
                        org.mockito.Mockito.any()))
                .thenReturn(0);
        when(mapper.findByUsernameForUpdate("admin")).thenReturn(limit);
        LoginFailureLimitService service = service(repository, mapper);

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
        LoginFailureLimitService service = service(repository, mock(LoginFailureLimitMapper.class));

        assertThatThrownBy(() -> service.assertLoginAllowed("admin"))
                .isInstanceOf(LoginBlockedException.class);
    }

    @Test
    @DisplayName("锁定过期后的失败重新开始计数并保留锁定级别")
    void recordFailureShouldStartNewWindowAfterLockExpired() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        LoginFailureLimitMapper mapper = mock(LoginFailureLimitMapper.class);
        LoginFailureLimit limit =
                limit(
                        2,
                        1,
                        LocalDateTime.now().minusMinutes(6),
                        LocalDateTime.now().minusMinutes(1));
        when(mapper.insertFirstFailureIfAbsent(
                        org.mockito.Mockito.eq("admin"),
                        org.mockito.Mockito.any(),
                        org.mockito.Mockito.any()))
                .thenReturn(0);
        when(mapper.findByUsernameForUpdate("admin")).thenReturn(limit);
        LoginFailureLimitService service = service(repository, mapper);

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
        LoginFailureLimitService service = service(repository, mock(LoginFailureLimitMapper.class));

        service.clear("admin");

        verify(repository).deleteById("admin");
    }

    @Test
    @DisplayName("首次失败写入未锁定状态")
    void firstFailureShouldInsertUnlockedState() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        LoginFailureLimitMapper mapper = mock(LoginFailureLimitMapper.class);
        when(mapper.insertFirstFailureIfAbsent(
                        org.mockito.Mockito.eq("admin"),
                        org.mockito.Mockito.any(),
                        org.mockito.Mockito.any()))
                .thenReturn(1);
        LoginFailureLimitService service = service(repository, mapper);

        service.recordFailure(" admin ");

        verify(mapper)
                .insertFirstFailureIfAbsent(
                        org.mockito.Mockito.eq("admin"),
                        org.mockito.Mockito.any(),
                        org.mockito.Mockito.any());
    }

    @Test
    @DisplayName("失败阈值为一次时首次失败后立即锁定")
    void firstFailureShouldLockWhenThresholdIsOne() {
        LoginFailureLimitDataRepository repository = mock(LoginFailureLimitDataRepository.class);
        LoginFailureLimitMapper mapper = mock(LoginFailureLimitMapper.class);
        LoginFailureLimit limit = limit(1, 0, LocalDateTime.now().minusSeconds(1), null);
        when(mapper.insertFirstFailureIfAbsent(
                        org.mockito.Mockito.eq("admin"),
                        org.mockito.Mockito.any(),
                        org.mockito.Mockito.any()))
                .thenReturn(1);
        when(mapper.findByUsernameForUpdate("admin")).thenReturn(limit);
        LoginFailureLimitService service = service(repository, mapper, 1);

        service.recordFailure("admin");

        assertThat(limit.getFailureCount()).isEqualTo(1);
        assertThat(limit.getLockLevel()).isEqualTo(1);
        assertThat(limit.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(4));
        verify(mapper).findByUsernameForUpdate("admin");
        verify(mapper).update(limit);
    }

    private static LoginFailureLimitService service(
            LoginFailureLimitDataRepository repository, LoginFailureLimitMapper mapper) {
        return service(repository, mapper, 2);
    }

    private static LoginFailureLimitService service(
            LoginFailureLimitDataRepository repository,
            LoginFailureLimitMapper mapper,
            int maxFailures) {
        return new LoginFailureLimitService(
                repository,
                mapper,
                new LoginFailureLimitProperties(
                        Duration.ofMinutes(10),
                        maxFailures,
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
