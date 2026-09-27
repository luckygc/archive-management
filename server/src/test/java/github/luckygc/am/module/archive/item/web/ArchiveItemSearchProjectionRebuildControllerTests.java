package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.api.JobAcceptedResponse;
import github.luckygc.am.common.api.JobStatusResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionRebuildService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("搜索投影重建任务 HTTP 入口")
class ArchiveItemSearchProjectionRebuildControllerTests {

    private final ArchiveItemSearchProjectionRebuildService rebuildService =
            mock(ArchiveItemSearchProjectionRebuildService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveItemSearchProjectionRebuildController controller =
            new ArchiveItemSearchProjectionRebuildController(rebuildService, permissionService);

    @Test
    @DisplayName("启动重建返回 202 和任务资源位置")
    void startRebuildReturnsAcceptedJob() {
        Authentication authentication = authentication(9L);
        JobAcceptedResponse accepted =
                new JobAcceptedResponse(
                        17L, "queued", "/api/v1/archive-search-projection-rebuild-jobs/17");
        when(rebuildService.start(3L, 9L)).thenReturn(accepted);

        var response = controller.startRebuild(3L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getFirst("Operation-Location"))
                .isEqualTo(accepted.operationLocation());
        assertThat(response.getBody()).isEqualTo(accepted);
        verify(permissionService)
                .requirePermission(9L, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
    }

    @Test
    @DisplayName("任务状态通过独立资源查询")
    void getRebuildJobReturnsStatusResource() {
        Authentication authentication = authentication(9L);
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 12, 0);
        JobStatusResponse status =
                new JobStatusResponse(
                        17L, "succeeded", 100, now, now, Map.of("rebuiltCount", 250), null, null);
        when(rebuildService.get(17L)).thenReturn(status);

        assertThat(controller.getRebuildJob(17L, authentication)).isEqualTo(status);

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
