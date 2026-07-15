package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService.DownloadLinkCreated;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.SearchArchiveItemsRequest;
import github.luckygc.am.module.archive.item.web.ArchiveItemImportExportController.ExportArchiveRecordsRequest;

@DisplayName("档案导入导出 HTTP 入口")
class ArchiveItemImportExportControllerTests {

    private final ArchiveItemImportExportService importExportService =
            mock(ArchiveItemImportExportService.class);
    private final ArchiveItemImportExportController controller =
            new ArchiveItemImportExportController(importExportService);

    @Test
    @DisplayName("创建导入模板用户短链")
    void createImportTemplateDownloadLinkShouldReturnInternalDownloadUrl() {
        Authentication authentication = authentication(9L);
        when(importExportService.createImportTemplateDownloadLink(1L, 9L))
                .thenReturn(
                        new DownloadLinkCreated(
                                "template-code", LocalDateTime.of(2026, 7, 15, 10, 10)));

        var response = controller.createImportTemplateDownloadLink(1L, authentication);

        assertThat(response.url()).isEqualTo("/api/v1/file-links/template-code:download");
        assertThat(response.expiresAt()).isEqualTo(LocalDateTime.of(2026, 7, 15, 10, 10));
        verify(importExportService).createImportTemplateDownloadLink(1L, 9L);
    }

    @Test
    @DisplayName("创建导出用户短链")
    void createExportDownloadLinkShouldForwardSearchRequest() {
        Authentication authentication = authentication(9L);
        ExportArchiveRecordsRequest request =
                new ExportArchiveRecordsRequest(1L, "F001", 77L, "合同", null, null, null);
        SearchArchiveItemsRequest internalRequest =
                new SearchArchiveItemsRequest(1L, "F001", "合同", null, null, null, null, null, 77L);
        when(importExportService.createExportDownloadLink(internalRequest, 9L))
                .thenReturn(
                        new DownloadLinkCreated(
                                "export-code", LocalDateTime.of(2026, 7, 15, 10, 10)));

        var response = controller.createExportDownloadLink(request, authentication);

        assertThat(response.url()).isEqualTo("/api/v1/file-links/export-code:download");
        verify(importExportService).createExportDownloadLink(internalRequest, 9L);
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
