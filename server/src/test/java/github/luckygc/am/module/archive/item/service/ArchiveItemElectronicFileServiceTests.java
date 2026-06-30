package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.common.storage.StorageType;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemElectronicFileRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemElectronicFileResponse;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDto;

@DisplayName("档案条目电子文件绑定")
class ArchiveItemElectronicFileServiceTests {

    private ArchiveMapper archiveMapper;
    private StorageObjectService storageObjectService;
    private ArchiveItemElectronicFileService electronicFileService;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        storageObjectService = mock(StorageObjectService.class);
        electronicFileService =
                new ArchiveItemElectronicFileService(archiveMapper, storageObjectService);
    }

    @Test
    @DisplayName("绑定存储对象记录到档案条目")
    void bindElectronicFileShouldInsertBinding() {
        when(archiveMapper.getArchiveItem(10L)).thenReturn(Map.of("id", 10L));
        when(storageObjectService.getActiveObject(20L)).thenReturn(storageObject());
        when(archiveMapper.insertArchiveItemElectronicFile(10L, 20L, "ORIGINAL", 7, 9L))
                .thenReturn(30L);

        ArchiveItemElectronicFileResponse response =
                electronicFileService.bindFile(
                        10L, new ArchiveItemElectronicFileRequest(20L, " ORIGINAL ", 7), 9L);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.archiveItemId()).isEqualTo(10L);
        assertThat(response.storageObjectId()).isEqualTo(20L);
        assertThat(response.usageType()).isEqualTo("ORIGINAL");
        assertThat(response.displayOrder()).isEqualTo(7);
        assertThat(response.originalFilename()).isEqualTo("demo.pdf");
        verify(archiveMapper).insertArchiveItemElectronicFile(10L, 20L, "ORIGINAL", 7, 9L);
    }

    @Test
    @DisplayName("查询档案条目电子文件绑定列表")
    void listFilesShouldReturnCollectionResponse() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 30, 10, 0);
        when(archiveMapper.getArchiveItem(10L)).thenReturn(Map.of("id", 10L));
        when(archiveMapper.listArchiveItemElectronicFiles(10L))
                .thenReturn(
                        List.of(
                                Map.ofEntries(
                                        Map.entry("id", 30L),
                                        Map.entry("archive_item_id", 10L),
                                        Map.entry("storage_object_id", 20L),
                                        Map.entry("usage_type", "ORIGINAL"),
                                        Map.entry("display_order", 7),
                                        Map.entry("original_filename", "demo.pdf"),
                                        Map.entry("file_size", 1024L),
                                        Map.entry("content_type", "application/pdf"),
                                        Map.entry("checksum_sha256", "abc"),
                                        Map.entry("created_at", createdAt))));

        CollectionResponse<ArchiveItemElectronicFileResponse> response =
                electronicFileService.listFiles(10L);

        assertThat(response.items())
                .containsExactly(
                        new ArchiveItemElectronicFileResponse(
                                30L,
                                10L,
                                20L,
                                "ORIGINAL",
                                7,
                                "demo.pdf",
                                1024L,
                                "application/pdf",
                                "abc",
                                createdAt));
    }

    @Test
    @DisplayName("解绑时删除绑定记录")
    void unbindFileShouldDeleteBinding() {
        when(archiveMapper.deleteArchiveItemElectronicFile(10L, 30L)).thenReturn(1);

        electronicFileService.unbindFile(10L, 30L, 9L);

        verify(archiveMapper).deleteArchiveItemElectronicFile(10L, 30L);
    }

    @Test
    @DisplayName("解绑不存在绑定时返回不存在")
    void unbindFileShouldRejectMissingBinding() {
        when(archiveMapper.deleteArchiveItemElectronicFile(10L, 30L)).thenReturn(0);

        assertThatThrownBy(() -> electronicFileService.unbindFile(10L, 30L, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("文件绑定不存在");
    }

    @Test
    @DisplayName("下载按绑定记录打开存储对象")
    void downloadFileShouldOpenBoundStorageObject() {
        FileStorageResource resource =
                new FileStorageResource(
                        StorageType.LOCAL,
                        "archive",
                        "2026/06/demo.pdf",
                        new ByteArrayInputStream(
                                "demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        4,
                        "application/pdf");
        when(archiveMapper.getArchiveItemElectronicFileStorageObjectId(10L, 30L)).thenReturn(20L);
        when(storageObjectService.openObject(20L))
                .thenReturn(new StorageObjectDownload("demo.pdf", resource));

        ArchiveItemElectronicFileService.ArchiveItemFileDownload download =
                electronicFileService.downloadFile(10L, 30L);

        assertThat(download.originalFilename()).isEqualTo("demo.pdf");
        assertThat(download.resource()).isSameAs(resource);
        verify(storageObjectService).openObject(20L);
    }

    private StorageObjectDto storageObject() {
        return new StorageObjectDto(
                20L,
                StorageType.LOCAL,
                "archive",
                "2026/06/demo.pdf",
                "demo.pdf",
                1024L,
                "application/pdf",
                "abc");
    }
}
