package github.luckygc.am.module.storage.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.FileLinkService.FileLinkTarget;
import github.luckygc.am.module.storage.service.FileLinkTargetResolver;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;

@DisplayName("文件短链下载 HTTP 入口")
class FileLinkDownloadControllerTests {

    @Test
    @DisplayName("短链下载固定使用 octet-stream 内容类型")
    void downloadShouldAlwaysUseOctetStream() {
        FileLinkService fileLinkService = mock(FileLinkService.class);
        FileLinkTargetResolver resolver = mock(FileLinkTargetResolver.class);
        FileLinkTarget target = new FileLinkTarget(FileLinkTargetType.STORAGE_OBJECT, null, 20L);
        when(fileLinkService.resolvePublic("abc")).thenReturn(target);
        when(resolver.targetType()).thenReturn(FileLinkTargetType.STORAGE_OBJECT);
        when(resolver.open(target, null))
                .thenReturn(
                        new StorageObjectDownload(
                                "demo.pdf",
                                new FileStorageResource(
                                        "archive",
                                        "2026/06/demo.pdf",
                                        new ByteArrayInputStream(
                                                "demo"
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)),
                                        4,
                                        "application/pdf")));
        FileLinkDownloadController controller =
                new FileLinkDownloadController(fileLinkService, List.of(resolver));

        ResponseEntity<InputStreamResource> response = controller.downloadPublic("abc");

        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("demo.pdf");
    }
}
