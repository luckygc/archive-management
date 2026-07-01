package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService;

@DisplayName("档案条目电子文件短链")
class ArchiveItemElectronicFileLinkServiceTests {

    private ArchiveMapper archiveMapper;
    private AuthorizationPermissionService permissionService;
    private ArchiveItemRoutingService archiveItemRoutingService;
    private FileLinkService fileLinkService;
    private ArchiveItemElectronicFileService electronicFileService;
    private ArchiveItemElectronicFileLinkService linkService;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        permissionService = mock(AuthorizationPermissionService.class);
        archiveItemRoutingService = mock(ArchiveItemRoutingService.class);
        fileLinkService = mock(FileLinkService.class);
        electronicFileService = mock(ArchiveItemElectronicFileService.class);
        when(permissionService.hasPermission(9L, "archive:file:download")).thenReturn(true);
        linkService =
                new ArchiveItemElectronicFileLinkService(
                        archiveMapper,
                        permissionService,
                        archiveItemRoutingService,
                        fileLinkService,
                        electronicFileService);
    }

    @Test
    @DisplayName("创建下载短链时校验权限和绑定关系并绑定当前用户")
    void createDownloadLinkShouldValidateAndBindCurrentUser() {
        when(archiveMapper.getArchiveItemElectronicFileStorageObjectId(10L, 30L)).thenReturn(20L);
        when(fileLinkService.createUserLink(
                        FileLinkTargetType.ARCHIVE_ITEM_ELECTRONIC_FILE,
                        10L,
                        30L,
                        Duration.ofMinutes(10),
                        9L))
                .thenReturn(
                        new FileLinkService.FileLinkCreated(
                                "AbCdEfGhIjKlMnOpQrStUv", LocalDateTime.of(2026, 7, 1, 10, 10)));

        ArchiveItemElectronicFileLinkService.DownloadLinkCreated created =
                linkService.createDownloadLink(10L, 30L, 9L);

        assertThat(created.code()).isEqualTo("AbCdEfGhIjKlMnOpQrStUv");
        assertThat(created.expiresAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 10));
        verify(archiveItemRoutingService).assertItemInDataScope(10L, 9L);
        verify(fileLinkService)
                .createUserLink(
                        FileLinkTargetType.ARCHIVE_ITEM_ELECTRONIC_FILE,
                        10L,
                        30L,
                        Duration.ofMinutes(10),
                        9L);
    }
}
