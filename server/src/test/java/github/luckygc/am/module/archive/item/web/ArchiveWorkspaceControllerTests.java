package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.common.security.UnauthenticatedException;
import github.luckygc.am.module.archive.item.service.ArchiveWorkspaceService;
import github.luckygc.am.module.archive.item.service.ArchiveWorkspaceService.ArchiveWorkspaceSummary;
import github.luckygc.am.module.archive.item.web.ArchiveWorkspaceController.WorkspaceSummaryResponse;

@DisplayName("档案工作台摘要 HTTP 入口")
class ArchiveWorkspaceControllerTests {

    private final ArchiveWorkspaceService service = mock(ArchiveWorkspaceService.class);
    private final ArchiveWorkspaceController controller = new ArchiveWorkspaceController(service);

    @Test
    @DisplayName("工作台摘要返回四个统计值")
    void getSummaryReturnsDedicatedResponse() {
        when(service.getSummary(8L)).thenReturn(new ArchiveWorkspaceSummary(12, 3, 2, 7));

        WorkspaceSummaryResponse response = controller.getSummary(auth());

        assertThat(response).isEqualTo(new WorkspaceSummaryResponse(12, 3, 2, 7));
        verify(service).getSummary(8L);
    }

    @Test
    @DisplayName("未认证请求进入统一 ProblemDetail 认证异常路径")
    void unauthenticatedRequestUsesGlobalProblemDetailPath() {
        assertThatThrownBy(() -> controller.getSummary(null))
                .isInstanceOf(UnauthenticatedException.class);
    }

    private static TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken(
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return 8L;
                    }

                    @Override
                    public String displayName() {
                        return "档案员";
                    }
                },
                null);
    }
}
