package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService.OperationMonitor;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionRebuildService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("搜索投影重建任务 HTTP 入口")
class ArchiveItemSearchProjectionRebuildControllerTests {

    private final ArchiveItemSearchProjectionRebuildService rebuildService =
            mock(ArchiveItemSearchProjectionRebuildService.class);
    private final ArchiveItemSearchProjectionOperationService operationService =
            mock(ArchiveItemSearchProjectionOperationService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveItemSearchProjectionRebuildController controller =
            new ArchiveItemSearchProjectionRebuildController(
                    rebuildService, operationService, permissionService);

    @Test
    @DisplayName("启动重建返回 202 和操作监视资源")
    void startRebuildReturnsAcceptedJob() {
        Authentication authentication = authentication(9L);
        when(rebuildService.start(3L, 9L)).thenReturn(17L);
        String operationId = ArchiveItemSearchProjectionOperationService.operationId(17L);
        OperationMonitor monitor =
                new OperationMonitor(
                        operationId, "NotStarted", "archiveSearchProjectionRebuild", null, null);
        when(operationService.get(operationId, 9L)).thenReturn(monitor);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURL())
                .thenReturn(
                        new StringBuffer(
                                "http://localhost:8080/archive-categories/3:rebuildSearchProjection"));

        var response = controller.startRebuild(3L, authentication, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getFirst("Operation-Location"))
                .isEqualTo("http://localhost:8080/operations/archive-search-projection-rebuild-17");
        assertThat(response.getHeaders().getFirst("Operation-Id"))
                .isEqualTo("archive-search-projection-rebuild-17");
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(response.getHeaders().getFirst("Location")).isNull();
        assertThat(response.getBody()).isEqualTo(monitor);
        verify(permissionService)
                .requirePermission(9L, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
    }

    private Authentication authentication(Long userId) {
        Authentication authentication = mock(Authentication.class);
        AuthenticatedUser user =
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return userId;
                    }

                    @Override
                    public String displayName() {
                        return "测试用户";
                    }
                };
        when(authentication.getPrincipal()).thenReturn(user);
        return authentication;
    }
}
