package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService.ArchiveExcelFile;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.SearchArchiveItemsRequest;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("档案导入导出 HTTP 入口")
class ArchiveItemImportExportControllerTests {

    private final ArchiveItemImportExportService importExportService =
            mock(ArchiveItemImportExportService.class);
    private final ArchiveItemImportExportController controller =
            new ArchiveItemImportExportController(
                    importExportService, JsonMapper.builder().findAndAddModules().build());

    @Test
    @DisplayName("链接导出从 query 参数还原查询条件")
    void exportItemsFromLinkShouldDecodeQueryParameter() {
        Authentication authentication = authentication(9L);
        String query =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                "{\"categoryId\":1,\"fondsCode\":\"F001\"}"
                                        .getBytes(StandardCharsets.UTF_8));
        when(importExportService.exportItems(
                        new SearchArchiveItemsRequest(
                                1L, "F001", null, null, null, null, null, null),
                        9L))
                .thenReturn(new ArchiveExcelFile("archive-export.xlsx", new byte[] {1, 2}));

        ResponseEntity<?> response = controller.exportItemsFromLink(query, authentication);

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("archive-export.xlsx");
        verify(importExportService)
                .exportItems(
                        new SearchArchiveItemsRequest(
                                1L, "F001", null, null, null, null, null, null),
                        9L);
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
