package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.common.storage.StorageType;
import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemElectronicFileResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.CreateArchiveItemElectronicFileRequest;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDto;

@DisplayName("档案条目电子文件")
class ArchiveItemElectronicFileServiceTests {

    private ArchiveMapper archiveMapper;
    private StorageObjectService storageObjectService;
    private ArchiveItemDataRepository archiveItemRepository;
    private ArchiveItemAuditDataRepository archiveItemAuditRepository;
    private AuthorizationPermissionService permissionService;
    private ArchiveItemRoutingService archiveItemRoutingService;
    private ArchiveItemElectronicFileService electronicFileService;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        storageObjectService = mock(StorageObjectService.class);
        archiveItemRepository = mock(ArchiveItemDataRepository.class);
        archiveItemAuditRepository = mock(ArchiveItemAuditDataRepository.class);
        permissionService = mock(AuthorizationPermissionService.class);
        archiveItemRoutingService = mock(ArchiveItemRoutingService.class);
        when(permissionService.hasPermission(9L, "archive:item:create")).thenReturn(true);
        when(permissionService.hasPermission(9L, "archive:item:update")).thenReturn(true);
        when(permissionService.hasPermission(9L, "archive:item:read")).thenReturn(true);
        when(permissionService.hasPermission(9L, "archive:item:delete")).thenReturn(true);
        when(permissionService.hasPermission(9L, "archive:item:download-electronic-file"))
                .thenReturn(true);
        electronicFileService =
                new ArchiveItemElectronicFileService(
                        archiveMapper,
                        storageObjectService,
                        archiveItemRepository,
                        archiveItemAuditRepository,
                        permissionService,
                        archiveItemRoutingService);
    }

    @Test
    @DisplayName("新增档案条目电子文件")
    void createElectronicFileShouldInsertElectronicFile() {
        when(archiveMapper.getArchiveItem(10L)).thenReturn(Map.of("id", 10L));
        when(storageObjectService.getActiveObjectForOwner(20L, 9L)).thenReturn(storageObject());
        when(archiveMapper.insertArchiveItemElectronicFile(10L, 20L, "ORIGINAL", 7, 9L))
                .thenReturn(30L);

        ArchiveItemElectronicFileResponse response =
                electronicFileService.createFile(
                        10L, new CreateArchiveItemElectronicFileRequest(20L, " ORIGINAL ", 7), 9L);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.archiveItemId()).isEqualTo(10L);
        assertThat(response.storageObjectId()).isEqualTo(20L);
        assertThat(response.usageType()).isEqualTo("ORIGINAL");
        assertThat(response.displayOrder()).isEqualTo(7);
        assertThat(response.originalFilename()).isEqualTo("demo.pdf");
        verify(storageObjectService).getActiveObjectForOwner(20L, 9L);
        verify(storageObjectService, never()).getActiveObject(20L);
        verify(archiveMapper).insertArchiveItemElectronicFile(10L, 20L, "ORIGINAL", 7, 9L);
    }

    @Test
    @DisplayName("有档案创建权限且档案在数据范围内时允许新增电子文件")
    void createElectronicFileShouldAllowItemCreatePermission() {
        when(archiveMapper.getArchiveItem(10L)).thenReturn(Map.of("id", 10L));
        when(storageObjectService.getActiveObjectForOwner(20L, 9L)).thenReturn(storageObject());
        when(archiveMapper.insertArchiveItemElectronicFile(10L, 20L, "ORIGINAL", 7, 9L))
                .thenReturn(30L);

        electronicFileService.createFile(
                10L, new CreateArchiveItemElectronicFileRequest(20L, " ORIGINAL ", 7), 9L);

        verify(archiveItemRoutingService).assertItemInDataScope(10L, 9L);
        verify(archiveMapper).insertArchiveItemElectronicFile(10L, 20L, "ORIGINAL", 7, 9L);
    }

    @Test
    @DisplayName("有档案编辑权限且无创建权限时允许新增电子文件")
    void createElectronicFileShouldAllowItemUpdatePermission() {
        when(permissionService.hasPermission(9L, "archive:item:create")).thenReturn(false);
        when(archiveMapper.getArchiveItem(10L)).thenReturn(Map.of("id", 10L));
        when(storageObjectService.getActiveObjectForOwner(20L, 9L)).thenReturn(storageObject());
        when(archiveMapper.insertArchiveItemElectronicFile(10L, 20L, "ORIGINAL", 7, 9L))
                .thenReturn(30L);

        electronicFileService.createFile(
                10L, new CreateArchiveItemElectronicFileRequest(20L, " ORIGINAL ", 7), 9L);

        verify(archiveMapper).insertArchiveItemElectronicFile(10L, 20L, "ORIGINAL", 7, 9L);
    }

    @Test
    @DisplayName("查询档案条目电子文件列表")
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
                electronicFileService.listFiles(10L, 9L);

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
    @DisplayName("删除档案条目电子文件")
    void deleteFileShouldDeleteElectronicFile() {
        when(archiveMapper.deleteArchiveItemElectronicFile(10L, 30L)).thenReturn(1);

        electronicFileService.deleteFile(10L, 30L, 9L);

        verify(archiveMapper).deleteArchiveItemElectronicFile(10L, 30L);
    }

    @Test
    @DisplayName("删除不存在电子文件时返回不存在")
    void deleteFileShouldRejectMissingElectronicFile() {
        when(archiveMapper.deleteArchiveItemElectronicFile(10L, 30L)).thenReturn(0);

        assertThatThrownBy(() -> electronicFileService.deleteFile(10L, 30L, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("档案电子文件不存在");
    }

    @Test
    @DisplayName("下载按档案电子文件打开存储对象")
    void downloadFileShouldOpenElectronicFileWithDownloadPermission() {
        FileStorageResource resource =
                new FileStorageResource(
                        StorageType.LOCAL,
                        "archive",
                        "2026/06/demo.pdf",
                        new ByteArrayInputStream(
                                "demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        4,
                        "application/pdf");
        when(archiveItemRepository.findById(10L)).thenReturn(Optional.of(archiveItem()));
        when(archiveMapper.getArchiveItemElectronicFileStorageObjectId(10L, 30L)).thenReturn(20L);
        when(storageObjectService.openObject(20L))
                .thenReturn(new StorageObjectDownload("demo.pdf", resource));

        ArchiveItemElectronicFileService.ArchiveItemFileDownload download =
                electronicFileService.downloadFile(10L, 30L, 9L);

        assertThat(download.originalFilename()).isEqualTo("demo.pdf");
        assertThat(download.resource()).isSameAs(resource);
        verify(storageObjectService).openObject(20L);
    }

    @Test
    @DisplayName("只有档案阅读权限没有下载电子文件权限时拒绝下载")
    void downloadFileShouldRejectReadOnlyPermission() {
        when(permissionService.hasPermission(9L, "archive:item:download-electronic-file"))
                .thenReturn(false);

        assertThatThrownBy(() -> electronicFileService.downloadFile(10L, 30L, 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(storageObjectService, never()).openObject(20L);
    }

    @Test
    @DisplayName("下载范围外档案文件时拒绝打开存储对象")
    void downloadFileShouldRejectItemOutsideDataScope() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足"))
                .when(archiveItemRoutingService)
                .assertItemInDataScope(10L, 9L);

        assertThatThrownBy(() -> electronicFileService.downloadFile(10L, 30L, 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(storageObjectService, never()).openObject(20L);
    }

    @Test
    @DisplayName("下载文件时写入下载审计")
    void downloadFileShouldInsertDownloadAudit() {
        FileStorageResource resource =
                new FileStorageResource(
                        StorageType.LOCAL,
                        "archive",
                        "2026/06/demo.pdf",
                        new ByteArrayInputStream(
                                "demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        4,
                        "application/pdf");
        when(archiveItemRepository.findById(10L)).thenReturn(Optional.of(archiveItem()));
        when(archiveMapper.getArchiveItemElectronicFileStorageObjectId(10L, 30L)).thenReturn(20L);
        when(storageObjectService.openObject(20L))
                .thenReturn(new StorageObjectDownload("demo.pdf", resource));

        electronicFileService.downloadFile(10L, 30L, 9L);

        ArgumentCaptor<ArchiveItemAudit> auditCaptor =
                ArgumentCaptor.forClass(ArchiveItemAudit.class);
        verify(archiveItemAuditRepository).insert(auditCaptor.capture());
        ArchiveItemAudit audit = auditCaptor.getValue();
        assertThat(audit.getSourceTableName()).isEqualTo("am_archive_item");
        assertThat(audit.getSourceRecordId()).isEqualTo(10L);
        assertThat(audit.getArchiveItemId()).isEqualTo(10L);
        assertThat(audit.getFondsCode()).isEqualTo("F001");
        assertThat(audit.getCategoryCode()).isEqualTo("contract");
        assertThat(audit.getOperationType()).isEqualTo("DOWNLOAD");
        assertThat(audit.getOperationReason()).isEqualTo("electronicFileId=30, storageObjectId=20");
        assertThat(audit.getOperatedBy()).isEqualTo(9L);
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
                "abc",
                9L);
    }

    private ArchiveItem archiveItem() {
        ArchiveItem archiveItem = new ArchiveItem();
        archiveItem.setId(10L);
        archiveItem.setFondsCode("F001");
        archiveItem.setCategoryCode("contract");
        return archiveItem;
    }
}
