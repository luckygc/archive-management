package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.CreateArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.UpdateArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService.CreateArchiveVolumeRequest;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案写入全宗校验")
class ArchiveItemFondsValidationTests {

    private ArchiveMapper archiveMapper;
    private ArchiveMetadataService archiveMetadataService;
    private ArchiveItemRoutingService archiveItemRoutingService;
    private ArchiveVolumeService archiveVolumeService;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        archiveMetadataService = mock(ArchiveMetadataService.class);
        ArchiveItemSearchProjectionService searchProjectionService =
                mock(ArchiveItemSearchProjectionService.class);
        ArchiveDataScopeService dataScopeService = mock(ArchiveDataScopeService.class);
        when(dataScopeService.buildItemFilter(anyLong(), anyLong(), anyString()))
                .thenReturn(ArchiveDataScopeFilter.all());
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        ArchiveItemAuditDataRepository auditRepository = mock(ArchiveItemAuditDataRepository.class);
        when(permissionService.hasPermission(anyLong(), anyString())).thenReturn(true);
        archiveItemRoutingService =
                new ArchiveItemRoutingService(
                        archiveMetadataService,
                        archiveMapper,
                        searchProjectionService,
                        dataScopeService,
                        permissionService,
                        auditRepository);
        archiveVolumeService =
                new ArchiveVolumeService(
                        archiveMapper, archiveMetadataService, archiveItemRoutingService);
    }

    @Test
    @DisplayName("创建档案条目时拒绝停用全宗")
    void createItemShouldRejectDisabledFonds() {
        ArchiveCategoryDto category = itemCategory();
        when(archiveMetadataService.getCategory(1L)).thenReturn(category);
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataService.getEnabledFondsByCode("F001"))
                .thenThrow(new BadRequestException("全宗不可用"));

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.createItem(
                                        new CreateArchiveItemRequest(
                                                1L, null, "F001", "A-001", 2026, "DRAFT", null,
                                                null, null, Map.of()),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("全宗不可用");

        verify(archiveMapper, never())
                .insertArchiveItem(
                        anyString(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        any(),
                        anyInt(),
                        any());
        verify(archiveMetadataService).getEnabledFondsByCode("F001");
        verify(archiveMetadataService, never()).getFondsByCode("F001");
    }

    @Test
    @DisplayName("更新档案条目时拒绝停用全宗")
    void updateItemShouldRejectDisabledFonds() {
        ArchiveCategoryDto category = itemCategory();
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow());
        when(archiveMetadataService.listCategories(null)).thenReturn(List.of(category));
        when(archiveMetadataService.listEffectiveFields(
                        eq(1L), eq(ArchiveLevel.ITEM), any(), any(), isNull()))
                .thenReturn(List.of());
        when(archiveMapper.loadDynamicRecord(anyString(), eq(10L))).thenReturn(Map.of());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataService.getEnabledFondsByCode("F001"))
                .thenThrow(new BadRequestException("全宗不可用"));

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.updateItem(
                                        10L,
                                        new UpdateArchiveItemRequest(
                                                null, "F001", "A-002", 2026, "DRAFT", null, null,
                                                null, Map.of()),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("全宗不可用");

        verify(archiveMapper, never())
                .updateArchiveItem(
                        anyLong(),
                        any(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        any(),
                        anyInt(),
                        any());
        verify(archiveMetadataService).getEnabledFondsByCode("F001");
        verify(archiveMetadataService, never()).getFondsByCode("F001");
    }

    @Test
    @DisplayName("创建案卷时拒绝停用全宗")
    void createVolumeShouldRejectDisabledFonds() {
        when(archiveMetadataService.getCategory(1L)).thenReturn(volumeCategory());
        when(archiveMetadataService.getEnabledFondsByCode("F001"))
                .thenThrow(new BadRequestException("全宗不可用"));

        assertThatThrownBy(
                        () ->
                                archiveVolumeService.createVolume(
                                        new CreateArchiveVolumeRequest(
                                                1L, "F001", "V-001", 2026, "DRAFT"),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("全宗不可用");

        verify(archiveMapper, never())
                .insertArchiveVolume(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyInt(),
                        any());
        verify(archiveMetadataService).getEnabledFondsByCode("F001");
        verify(archiveMetadataService, never()).getFondsByCode("F001");
    }

    private ArchiveCategoryDto itemCategory() {
        return category(ArchiveManagementMode.ITEM_ONLY, null, "am_archive_item_contract");
    }

    private ArchiveCategoryDto volumeCategory() {
        return category(
                ArchiveManagementMode.VOLUME_ITEM,
                "am_archive_volume_contract",
                "am_archive_item_contract");
    }

    private ArchiveCategoryDto category(
            ArchiveManagementMode managementMode, String volumeTableName, String itemTableName) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return new ArchiveCategoryDto(
                1L,
                null,
                "contract",
                "合同档案",
                managementMode,
                volumeTableName,
                itemTableName,
                null,
                null,
                ArchiveTableStatus.BUILT,
                now,
                true,
                0,
                now,
                now);
    }

    private Map<String, Object> itemRow() {
        return Map.ofEntries(
                Map.entry("id", 10L),
                Map.entry("archiveLevel", ArchiveLevel.ITEM.value()),
                Map.entry("volumeId", 20L),
                Map.entry("fondsCode", "F000"),
                Map.entry("fondsName", "原全宗"),
                Map.entry("categoryCode", "contract"),
                Map.entry("categoryName", "合同档案"),
                Map.entry("archiveNo", "A-001"),
                Map.entry("electronicStatus", "DRAFT"),
                Map.entry("archiveYear", 2026),
                Map.entry("lockedFlag", false));
    }
}
