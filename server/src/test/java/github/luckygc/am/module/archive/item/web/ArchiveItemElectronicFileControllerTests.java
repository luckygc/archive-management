package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.common.storage.StorageType;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemFileDownload;

@DisplayName("档案条目电子文件 HTTP 入口")
class ArchiveItemElectronicFileControllerTests {

    private final ArchiveItemElectronicFileService electronicFileService =
            mock(ArchiveItemElectronicFileService.class);
    private final ArchiveItemElectronicFileController controller =
            new ArchiveItemElectronicFileController(electronicFileService);

    @Test
    @DisplayName("下载响应使用原始文件名和内容类型")
    void downloadFileShouldReturnResourceResponse() throws Exception {
        FileStorageResource resource =
                new FileStorageResource(
                        StorageType.LOCAL,
                        "archive",
                        "2026/06/demo.pdf",
                        new ByteArrayInputStream(
                                "demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        4,
                        "application/pdf");
        when(electronicFileService.downloadFile(10L, 30L, 9L))
                .thenReturn(new ArchiveItemFileDownload("demo.pdf", resource));

        ResponseEntity<InputStreamResource> response =
                controller.downloadFile(10L, 30L, authentication(9L));

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("demo.pdf");
        verify(electronicFileService).downloadFile(10L, 30L, 9L);
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
