package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService.OperationMonitor;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService.OperationPage;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("操作监视 HTTP 入口")
class ArchiveItemSearchProjectionOperationControllerTests {

    private final ArchiveItemSearchProjectionOperationService operationService =
            mock(ArchiveItemSearchProjectionOperationService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveItemSearchProjectionOperationController controller =
            new ArchiveItemSearchProjectionOperationController(operationService, permissionService);

    @Test
    @DisplayName("用户任务列表返回可继续请求的分页链接")
    void listReturnsNavigationLinks() {
        OperationMonitor monitor =
                new OperationMonitor(
                        "archive-search-projection-rebuild-17",
                        "NotStarted",
                        "archiveSearchProjectionRebuild",
                        null,
                        null);
        when(operationService.list(9L, 20, null))
                .thenReturn(new OperationPage(List.of(monitor), null, "next-token"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://localhost:8080/operations"));

        var response = controller.listOperations(20, null, authentication(9L), request);

        assertThat(response.items()).containsExactly(monitor);
        assertThat(response.self()).isEqualTo("http://localhost:8080/operations?limit=20");
        assertThat(response.next())
                .isEqualTo("http://localhost:8080/operations?limit=20&cursor=next-token");
        assertThat(response.prev()).isNull();
        verify(permissionService)
                .requirePermission(9L, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
    }

    @Test
    @DisplayName("运行中任务提示客户端轮询间隔")
    void runningOperationReturnsRetryAfter() {
        String id = "archive-search-projection-rebuild-17";
        OperationMonitor monitor =
                new OperationMonitor(id, "Running", "archiveSearchProjectionRebuild", null, null);
        when(operationService.get(id, 9L)).thenReturn(monitor);

        var response = controller.getOperation(id, authentication(9L));

        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
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
