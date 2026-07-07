package github.luckygc.am.module.archive.item.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.SearchArchiveItemsRequest;

@DisplayName("档案条目 HTTP 入口")
class ArchiveItemControllerTests {

    private final ArchiveItemRoutingService archiveItemRoutingService =
            mock(ArchiveItemRoutingService.class);
    private final ArchiveItemController controller =
            new ArchiveItemController(archiveItemRoutingService);

    @Test
    @DisplayName("搜索接口从 URL 查询参数接收 cursor 分页控制")
    void searchItemsShouldUseUrlQueryPageControls() {
        SearchArchiveItemsRequest body =
                new SearchArchiveItemsRequest(1L, "F001", "合同", null, null, 10, "body", null);
        Authentication authentication = authentication(9L);
        CursorPageTokenContext context = new CursorPageTokenContext("fingerprint");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        CursorPageRequest page = CursorPageRequest.of(50, cursor, false, context);

        controller.searchItems(body, page, authentication);

        verify(archiveItemRoutingService)
                        .searchItems(
                                new SearchArchiveItemsRequest(
                                        1L, "F001", "合同", null, null, 50, cursor, null),
                        9L,
                        context);
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
