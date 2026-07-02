package github.luckygc.am.module.authentication.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.module.authentication.ArchiveUserDetails;
import github.luckygc.am.module.authentication.service.AuthenticationAuditService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("登录会话控制器")
class LoginSessionControllerTests {

    @Test
    @DisplayName("列出登录会话时要求会话管理权限")
    void listLoginSessionsShouldRequireSessionManagePermission() {
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        LoginSessionController controller =
                new LoginSessionController(auditService, permissionService);
        UsernamePasswordAuthenticationToken authentication = authentication(7L);
        when(permissionService.hasPermission(
                        7L, AuthorizationPermissionCode.AUTHENTICATION_SESSION_MANAGE.code()))
                .thenReturn(false);

        assertThatThrownBy(
                        () ->
                                controller.listLoginSessions(
                                        pageRequest(),
                                        mock(HttpServletRequest.class),
                                        authentication))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(
                                                exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("查询认证审计时要求审计读取权限")
    void listLoginLogsShouldRequireAuditReadPermission() {
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        LoginSessionController controller =
                new LoginSessionController(auditService, permissionService);
        UsernamePasswordAuthenticationToken authentication = authentication(7L);
        when(permissionService.hasPermission(
                        7L, AuthorizationPermissionCode.AUTHENTICATION_AUDIT_READ.code()))
                .thenReturn(false);

        assertThatThrownBy(
                        () ->
                                controller.listLoginLogs(
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        pageRequest(),
                                        authentication))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(
                                                exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("踢出其他登录会话时要求会话管理权限")
    void revokeOtherSessionShouldRequireSessionManagePermission() {
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        LoginSessionController controller =
                new LoginSessionController(auditService, permissionService);
        UsernamePasswordAuthenticationToken authentication = authentication(7L);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(permissionService.hasPermission(
                        7L, AuthorizationPermissionCode.AUTHENTICATION_SESSION_MANAGE.code()))
                .thenReturn(true);

        controller.deleteLoginSession(
                "target-session",
                request,
                new org.springframework.mock.web.MockHttpServletResponse(),
                authentication);

        verify(auditService).revokeSession("target-session", request, authentication);
    }

    private static CursorPageRequest pageRequest() {
        return CursorPageRequest.of(
                20, null, false, new CursorPageTokenContext("test", "fingerprint", "user:7"));
    }

    private static UsernamePasswordAuthenticationToken authentication(Long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new ArchiveUserDetails(
                        userId, "user" + userId, "N/A", true, "用户" + userId, List.of()),
                "N/A",
                List.of());
    }
}
