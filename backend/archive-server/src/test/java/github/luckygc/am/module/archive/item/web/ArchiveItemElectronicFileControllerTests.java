package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileLinkService;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService;

@DisplayName("档案条目电子文件 HTTP 入口")
class ArchiveItemElectronicFileControllerTests {

    private final ArchiveItemElectronicFileService electronicFileService =
            mock(ArchiveItemElectronicFileService.class);
    private final ArchiveItemElectronicFileLinkService electronicFileLinkService =
            mock(ArchiveItemElectronicFileLinkService.class);
    private final ArchiveItemElectronicFileController controller =
            new ArchiveItemElectronicFileController(
                    electronicFileService, electronicFileLinkService);

    @Test
    @DisplayName("上传附件时从路径携带档案 ID，不接收用户手填文件记录 ID")
    void uploadFileShouldPassArchiveItemIdAndMultipartFile() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "合同.pdf", "application/pdf", "demo".getBytes());
        when(electronicFileService.uploadFile(
                        org.mockito.ArgumentMatchers.eq(10L),
                        any(
                                ArchiveItemElectronicFileService
                                        .UploadArchiveItemElectronicFileCommand.class),
                        org.mockito.ArgumentMatchers.eq(9L)))
                .thenReturn(
                        new ArchiveItemElectronicFileService.ArchiveItemElectronicFileResponse(
                                30L,
                                10L,
                                20L,
                                "DEFAULT",
                                0,
                                "合同.pdf",
                                4,
                                "application/pdf",
                                null,
                                LocalDateTime.of(2026, 7, 1, 10, 0)));

        ArchiveItemElectronicFileService.ArchiveItemElectronicFileResponse response =
                controller.uploadFile(10L, file, null, null, authentication(9L));

        assertThat(response.archiveItemId()).isEqualTo(10L);
        assertThat(response.originalFilename()).isEqualTo("合同.pdf");
        verify(electronicFileService)
                .uploadFile(
                        org.mockito.ArgumentMatchers.eq(10L),
                        any(
                                ArchiveItemElectronicFileService
                                        .UploadArchiveItemElectronicFileCommand.class),
                        org.mockito.ArgumentMatchers.eq(9L));
    }

    @Test
    @DisplayName("创建下载短链响应返回内部短链地址和过期时间")
    void createDownloadLinkShouldReturnInternalDownloadLink() {
        when(electronicFileLinkService.createDownloadLink(10L, 30L, 9L))
                .thenReturn(
                        new ArchiveItemElectronicFileLinkService.DownloadLinkCreated(
                                "AbCdEfGhIjKlMnOpQrStUv", LocalDateTime.of(2026, 7, 1, 10, 10)));

        ArchiveItemElectronicFileController.ArchiveItemElectronicFileDownloadLinkResponse response =
                controller.createDownloadLink(10L, 30L, authentication(9L));

        assertThat(response.url()).isEqualTo("/api/v1/file-links/AbCdEfGhIjKlMnOpQrStUv:download");
        assertThat(response.expiresAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 10));
        verify(electronicFileLinkService).createDownloadLink(10L, 30L, 9L);
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
