package github.luckygc.am.infrastructure.audit;

import java.time.Clock;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import github.luckygc.am.common.security.AuthenticatedUsers;

@Component
public class AuditContextProvider {

    private final Clock clock;

    public AuditContextProvider(Clock clock) {
        this.clock = clock;
    }

    public AuditContext current() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new AuditContext(now, currentUserId());
    }

    private @Nullable Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return AuthenticatedUsers.currentUserId(authentication.getPrincipal());
    }
}
