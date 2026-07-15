package github.luckygc.am.module.storage.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.FileLinkService.FileLinkTarget;
import github.luckygc.am.module.storage.service.FileLinkTargetResolver;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;

@DisplayName("文件短链下载 HTTP 入口")
class FileLinkDownloadControllerTests {

    @Test
    @DisplayName("内部短链按当前用户解析并返回附件")
    void downloadInternalShouldResolveBoundUser() {
        FileLinkService fileLinkService = mock(FileLinkService.class);
        FileLinkTargetResolver resolver = mock(FileLinkTargetResolver.class);
        Authentication authentication = mock(Authentication.class);
        AuthenticatedUser user =
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return 9L;
                    }

                    @Override
                    public String displayName() {
                        return "测试用户";
                    }
                };
        FileLinkTarget target = new FileLinkTarget(FileLinkTargetType.STORAGE_OBJECT, null, 20L);
        when(authentication.getPrincipal()).thenReturn(user);
        when(fileLinkService.resolveInternal("short-code", 9L)).thenReturn(target);
        when(resolver.targetType()).thenReturn(FileLinkTargetType.STORAGE_OBJECT);
        when(resolver.open(target, 9L))
                .thenReturn(
                        new StorageObjectDownload(
                                "archive-export.xlsx",
                                new FileStorageResource(
                                        "archive",
                                        "temporary/archive-export.xlsx",
                                        new ByteArrayInputStream(new byte[] {1, 2}),
                                        2,
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
        FileLinkDownloadController controller =
                new FileLinkDownloadController(fileLinkService, List.of(resolver));

        ResponseEntity<InputStreamResource> response =
                controller.downloadInternal("short-code", authentication);

        verify(fileLinkService).resolveInternal("short-code", 9L);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("archive-export.xlsx");
    }

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
