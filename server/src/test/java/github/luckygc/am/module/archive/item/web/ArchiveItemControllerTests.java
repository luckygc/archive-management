package github.luckygc.am.module.archive.item.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemCommandService;
import github.luckygc.am.module.archive.item.service.ArchiveItemLockService;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.SearchArchiveItemsRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService;

@DisplayName("档案条目 HTTP 入口")
class ArchiveItemControllerTests {

    private final ArchiveItemCommandService archiveItemRoutingService =
            mock(ArchiveItemCommandService.class);
    private final ArchiveItemQueryService archiveItemQueryService =
            mock(ArchiveItemQueryService.class);
    private final ArchiveItemReadService archiveItemReadService =
            mock(ArchiveItemReadService.class);
    private final ArchiveItemRelationService archiveItemRelationService =
            mock(ArchiveItemRelationService.class);
    private final ArchiveItemLockService archiveItemLockService =
            mock(ArchiveItemLockService.class);
    private final ArchiveItemController controller =
            new ArchiveItemController(
                    archiveItemRoutingService,
                    archiveItemQueryService,
                    archiveItemReadService,
                    archiveItemRelationService,
                    archiveItemLockService);

    @Test
    @DisplayName("搜索接口从 URL 查询参数接收 cursor 分页控制")
    void searchItemsShouldUseUrlQueryPageControls() {
        SearchArchiveItemsRequest body =
                new SearchArchiveItemsRequest(1L, "F001", "合同", null, null, 10, "body", null, 12L);
        Authentication authentication = authentication(9L);
        CursorPageTokenContext context = new CursorPageTokenContext("fingerprint");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        PageRequest page = CursorPageTokenCodec.pageRequest(50, cursor, false, context);

        controller.searchItems(body, page, authentication);

        verify(archiveItemQueryService).searchItems(body, 9L, page);
        org.assertj.core.api.Assertions.assertThat(body.volumeId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("关系列表把 depth 和 cursor 分页请求传给服务")
    void listRelationsShouldUseCursorPageAndDepth() {
        Authentication authentication = authentication(9L);
        PageRequest page = PageRequest.ofSize(100);
        @SuppressWarnings("unchecked")
        CursorPageResponse<ArchiveItemRelationService.ArchiveItemRelationResponse> response =
                mock(CursorPageResponse.class);
        when(archiveItemRelationService.listRelations(1L, 2, page, 9L)).thenReturn(response);

        var actual = controller.listRelations(1L, 2, page, authentication);

        org.assertj.core.api.Assertions.assertThat(actual).isSameAs(response);
        verify(archiveItemRelationService).listRelations(1L, 2, page, 9L);
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
