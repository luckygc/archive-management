package github.luckygc.am.module.authentication.web;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authentication.service.AuthenticationAuditService;
import github.luckygc.am.module.authentication.service.LoginFailureLimitService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class LoginSessionController {

    private final AuthenticationAuditService authenticationAuditService;
    private final LoginFailureLimitService failureLimitService;
    private final AuthorizationPermissionService permissionService;
    private final SecurityContextLogoutHandler securityContextLogoutHandler =
            new SecurityContextLogoutHandler();

    public LoginSessionController(
            AuthenticationAuditService authenticationAuditService,
            LoginFailureLimitService failureLimitService,
            AuthorizationPermissionService permissionService) {
        this.authenticationAuditService = authenticationAuditService;
        this.failureLimitService = failureLimitService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/me")
    public CurrentUserDto me(Authentication authentication, HttpServletRequest request) {
        return CurrentUserDto.from(authentication, currentSessionId(request));
    }

    @GetMapping("/api/v1/login-sessions")
    public CursorPageResponse<AuthenticationAuditService.LoginSessionResponse> listLoginSessions(
            CursorPageRequest page, HttpServletRequest request, Authentication authentication) {
        requirePermission(
                authentication, AuthorizationPermissionCode.AUTHENTICATION_SESSION_MANAGE);
        return authenticationAuditService.listLoginSessions(page, request);
    }

    @DeleteMapping("/api/v1/login-sessions/{session}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLoginSession(
            @PathVariable String session,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        if (session.equals(currentSessionId(request))) {
            authenticationAuditService.recordLogout(request, authentication);
            securityContextLogoutHandler.logout(request, response, authentication);
            return;
        }
        requirePermission(
                authentication, AuthorizationPermissionCode.AUTHENTICATION_SESSION_MANAGE);
        authenticationAuditService.revokeSession(session, request, authentication);
    }

    @PostMapping("/api/v1/login-failure-limits/{username}:reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetLoginFailureLimit(
            @PathVariable String username, Authentication authentication) {
        requirePermission(
                authentication, AuthorizationPermissionCode.AUTHENTICATION_SESSION_MANAGE);
        failureLimitService.clear(username);
    }

    @GetMapping("/api/v1/authentication-events")
    public CursorPageResponse<AuthenticationAuditService.LoginLogResponse> listLoginLogs(
            @RequestParam(required = false) @Nullable String eventType,
            @RequestParam(required = false) @Nullable String username,
            @RequestParam(required = false) @Nullable String keyword,
            @RequestParam(required = false) @Nullable LocalDateTime occurredAfter,
            @RequestParam(required = false) @Nullable LocalDateTime occurredBefore,
            CursorPageRequest page,
            Authentication authentication) {
        requirePermission(authentication, AuthorizationPermissionCode.AUTHENTICATION_AUDIT_READ);
        return authenticationAuditService.listLoginLogs(
                eventType, username, keyword, occurredAfter, occurredBefore, page);
    }

    private void requirePermission(
            @Nullable Authentication authentication, AuthorizationPermissionCode permissionCode) {
        Long userId =
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal());
        if (!permissionService.hasPermission(userId, permissionCode.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private @Nullable String currentSessionId(@Nullable HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        return session == null ? null : session.getId();
    }

    public record CurrentUserDto(
            @Nullable String sessionId, String username, String displayName, List<String> roles) {

        public static CurrentUserDto from(
                Authentication authentication, @Nullable String sessionId) {
            List<String> roles =
                    authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .map(authority -> Strings.CS.removeStart(authority, "ROLE_"))
                            .toList();
            String username = authentication.getName();
            String displayName =
                    authentication.getPrincipal() instanceof AuthenticatedUser userDetails
                            ? userDetails.displayName()
                            : username;
            return new CurrentUserDto(sessionId, username, displayName, roles);
        }
    }
}
