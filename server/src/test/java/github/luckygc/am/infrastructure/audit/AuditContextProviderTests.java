package github.luckygc.am.infrastructure.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import github.luckygc.am.common.security.AuthenticatedUser;

class AuditContextProviderTests {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-16T02:30:00Z"), ZONE_ID);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 10, 30);

    private final AuditContextProvider provider = new AuditContextProvider(CLOCK);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnCurrentTimeAndUserIdForAuthenticatedUser() {
        setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new TestAuthenticatedUser(42L), "N/A", List.of()));

        AuditContext context = provider.current();

        assertThat(context.now()).isEqualTo(NOW);
        assertThat(context.userId()).isEqualTo(42L);
    }

    @Test
    void shouldReturnCurrentTimeWithoutUserIdWhenAuthenticationIsAbsent() {
        AuditContext context = provider.current();

        assertThat(context.now()).isEqualTo(NOW);
        assertThat(context.userId()).isNull();
    }

    @Test
    void shouldReturnNullUserIdForAnonymousAuthentication() {
        setAuthentication(
                new AnonymousAuthenticationToken(
                        "test-key",
                        new TestAuthenticatedUser(42L),
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(provider.current().userId()).isNull();
    }

    @Test
    void shouldRejectNullCurrentTime() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AuditContext(null, null))
                .withMessage("now");
    }

    @Test
    void shouldReadClockInstantOncePerCurrentContext() {
        CountingClock countingClock = new CountingClock(CLOCK);

        new AuditContextProvider(countingClock).current();

        assertThat(countingClock.instantReadCount()).isOne();
    }

    @Test
    void shouldReturnNullUserIdForUnauthenticatedRecognizablePrincipal() {
        setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        new TestAuthenticatedUser(42L), "N/A"));

        assertThat(provider.current().userId()).isNull();
    }

    @Test
    void shouldReturnNullUserIdForUnsupportedPrincipal() {
        setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "unsupported-principal", "N/A", List.of()));

        AuditContext context = provider.current();

        assertThat(context.now()).isEqualTo(NOW);
        assertThat(context.userId()).isNull();
    }

    private void setAuthentication(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private record TestAuthenticatedUser(Long id) implements AuthenticatedUser {

        @Override
        public String displayName() {
            return "测试用户";
        }
    }

    private static final class CountingClock extends Clock {

        private final Clock delegate;
        private int instantReadCount;

        private CountingClock(Clock delegate) {
            this.delegate = delegate;
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new CountingClock(delegate.withZone(zone));
        }

        @Override
        public Instant instant() {
            instantReadCount++;
            return delegate.instant();
        }

        private int instantReadCount() {
            return instantReadCount;
        }
    }
}
