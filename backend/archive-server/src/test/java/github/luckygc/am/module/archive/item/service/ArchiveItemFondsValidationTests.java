package github.luckygc.am.module.archive.item.service;

import static github.luckygc.am.test.ArchiveTestFixtures.activeFondsDto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveVolumeDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemService.CreateArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemService.ReassignArchiveItemFondsRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemService.UpdateArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService.CreateArchiveVolumeRequest;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案写入全宗校验")
class ArchiveItemFondsValidationTests {

    private ArchiveMapper archiveMapper;
    private ArchiveMetadataService archiveMetadataService;
    private ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private ArchiveCategoryService archiveCategoryService;
    private ArchiveItemService archiveItemRoutingService;
    private ArchiveVolumeService archiveVolumeService;
    private ArchiveItemAuditDataRepository auditRepository;
    private ArchiveItemSearchProjectionSynchronizer searchProjectionSynchronizer;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        archiveMetadataService = mock(ArchiveMetadataService.class);
        archiveMetadataReferenceService = mock(ArchiveMetadataReferenceService.class);
        archiveCategoryService = mock(ArchiveCategoryService.class);
        searchProjectionSynchronizer = mock(ArchiveItemSearchProjectionSynchronizer.class);
        ArchiveDataScopeService dataScopeService = mock(ArchiveDataScopeService.class);
        when(dataScopeService.buildItemFilter(anyLong(), anyLong(), anyString()))
                .thenReturn(ArchiveDataScopeFilter.all());
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        auditRepository = mock(ArchiveItemAuditDataRepository.class);
        when(permissionService.hasPermission(anyLong(), anyString())).thenReturn(true);
        ArchiveItemReadService archiveItemReadService =
                new ArchiveItemReadService(
                        archiveMetadataService,
                        archiveCategoryService,
                        archiveMapper,
                        dataScopeService,
                        permissionService);
        archiveItemRoutingService =
                new ArchiveItemService(
                        archiveMetadataService,
                        archiveMetadataReferenceService,
                        archiveCategoryService,
                        archiveMapper,
                        searchProjectionSynchronizer,
                        dataScopeService,
                        permissionService,
                        auditRepository,
                        archiveItemReadService,
                        new ArchiveItemFieldValueConverter(),
                        ArchiveRuntimeTestSupport.passthroughExecutionService(),
                        ArchiveRuntimeTestSupport.traceService());
        archiveVolumeService =
                new ArchiveVolumeService(
                        archiveMapper,
                        mock(ArchiveVolumeDataRepository.class),
                        archiveMetadataService,
                        archiveMetadataReferenceService,
                        archiveCategoryService,
                        archiveItemReadService,
                        permissionService,
                        dataScopeService,
                        ArchiveRuntimeTestSupport.passthroughExecutionService(),
                        ArchiveRuntimeTestSupport.traceService());
    }

    @Test
    @DisplayName("创建档案条目时拒绝停用全宗")
    void createItemShouldRejectDisabledFonds() {
        ArchiveCategoryDto category = itemCategory();
        when(archiveCategoryService.getCategory(1L)).thenReturn(category);
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataReferenceService.getWritableFondsByCode("F001"))
                .thenThrow(new BadRequestException("全宗不可用"));

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.createItem(
                                        new CreateArchiveItemRequest(
                                                1L, null, "F001", "A-001", 2026, null, null, null,
                                                Map.of()),
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
                        any(),
                        any(),
                        anyInt());
        verify(archiveMetadataReferenceService).getWritableFondsByCode("F001");
        verify(archiveMetadataReferenceService, never()).getFondsByCode("F001");
    }

    @Test
    @DisplayName("创建档案条目时拒绝全宗未配置的分类")
    void createItemShouldRejectCategoryOutsideFondsScope() {
        when(archiveCategoryService.getCategory(1L)).thenReturn(itemCategory());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataReferenceService.getWritableFondsByCode("F001")).thenReturn(fonds());
        doThrow(new BadRequestException("该全宗未配置此分类", "categoryId", "该全宗未配置此分类"))
                .when(archiveCategoryService)
                .requireCategoryAvailableForFonds("F001", 1L);

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.createItem(
                                        new CreateArchiveItemRequest(
                                                1L, null, "F001", "A-001", 2026, null, null, null,
                                                Map.of()),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("该全宗未配置此分类");

        verify(archiveMapper, never())
                .insertArchiveItem(
                        anyString(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        any(),
                        anyInt());
    }

    @Test
    @DisplayName("更新档案条目时拒绝停用全宗")
    void updateItemShouldRejectDisabledFonds() {
        ArchiveCategoryDto category = itemCategory();
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow());
        when(archiveCategoryService.listCategories(null)).thenReturn(List.of(category));
        when(archiveMetadataService.listEffectiveFields(
                        eq(1L), eq(ArchiveLevel.ITEM), any(), any(), isNull()))
                .thenReturn(List.of());
        when(archiveMapper.loadDynamicRecord(anyString(), eq(10L))).thenReturn(Map.of());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataReferenceService.getWritableFondsByCode("F001"))
                .thenThrow(new BadRequestException("全宗不可用"));

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.updateItem(
                                        10L,
                                        new UpdateArchiveItemRequest(
                                                null, "F001", "A-002", 2026, null, null, null,
                                                Map.of()),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("全宗不可用");

        verify(archiveMapper, never())
                .updateArchiveItem(
                        anyLong(), any(), anyString(), anyString(), any(), any(), any(), anyInt());
        verify(archiveMetadataReferenceService).getWritableFondsByCode("F001");
        verify(archiveMetadataReferenceService, never()).getFondsByCode("F001");
    }

    @Test
    @DisplayName("更新档案条目时拒绝全宗未配置的分类")
    void updateItemShouldRejectCategoryOutsideFondsScope() {
        ArchiveCategoryDto category = itemCategory();
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow());
        when(archiveCategoryService.listCategories(null)).thenReturn(List.of(category));
        when(archiveMetadataService.listEffectiveFields(
                        eq(1L), eq(ArchiveLevel.ITEM), any(), any(), isNull()))
                .thenReturn(List.of());
        when(archiveMapper.loadDynamicRecord(anyString(), eq(10L))).thenReturn(Map.of());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataReferenceService.getWritableFondsByCode("F001")).thenReturn(fonds());
        doThrow(new BadRequestException("该全宗未配置此分类", "categoryId", "该全宗未配置此分类"))
                .when(archiveCategoryService)
                .requireCategoryAvailableForFonds("F001", 1L);

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.updateItem(
                                        10L,
                                        new UpdateArchiveItemRequest(
                                                null, "F001", "A-002", 2026, null, null, null,
                                                Map.of()),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("该全宗未配置此分类");

        verify(archiveMapper, never())
                .updateArchiveItem(
                        anyLong(), any(), anyString(), anyString(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("普通编辑不得调整已归档档案的全宗")
    void ordinaryUpdateCannotReassignArchivedItem() {
        prepareItemUpdate(itemRow(false, "F000", true), itemRow(false, "F000", true));
        when(archiveMetadataReferenceService.getWritableFondsByCode("F001")).thenReturn(fonds());

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.updateItem(
                                        10L,
                                        new UpdateArchiveItemRequest(
                                                null, "F001", "A-001", 2026, null, null, null,
                                                null),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("必须通过调整全宗动作办理");

        verify(archiveMapper, never())
                .updateArchiveItem(
                        anyLong(), any(), anyString(), anyString(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("卷内档案不能脱离案卷单独调整全宗")
    void reassignFondsRejectsItemInVolume() {
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow(true, "F000", true));
        when(archiveCategoryService.listCategories(null)).thenReturn(List.of(itemCategory()));
        when(archiveMetadataService.listEffectiveFields(
                        eq(1L), eq(ArchiveLevel.ITEM), any(), any(), isNull()))
                .thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.reassignFonds(
                                        10L,
                                        new ReassignArchiveItemFondsRequest("F001", "纠正归属"),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("卷内档案不能单独调整全宗");

        verify(archiveMetadataReferenceService, never()).getWritableFondsByCode(anyString());
    }

    @Test
    @DisplayName("专用动作调整全宗后同步投影并写入带原因的审计")
    void reassignFondsUpdatesItemAndWritesAudit() {
        prepareItemUpdate(itemRow(false, "F000", true), itemRow(false, "F001", true));
        when(archiveMetadataReferenceService.getWritableFondsByCode("F001")).thenReturn(fonds());
        when(archiveMapper.updateArchiveItem(
                        eq(10L),
                        isNull(),
                        eq("F001"),
                        eq("测试全宗"),
                        eq("A-001"),
                        any(),
                        any(),
                        eq(2026)))
                .thenReturn(1);

        var result =
                archiveItemRoutingService.reassignFonds(
                        10L, new ReassignArchiveItemFondsRequest("F001", "纠正历史归属"), 9L);

        assertThat(result.item().fondsCode()).isEqualTo("F001");
        ArgumentCaptor<ArchiveItemAudit> auditCaptor =
                ArgumentCaptor.forClass(ArchiveItemAudit.class);
        verify(auditRepository).insert(auditCaptor.capture());
        verify(searchProjectionSynchronizer).synchronize(10L);
        assertThat(auditCaptor.getValue().getOperationType()).isEqualTo("REASSIGN_FONDS");
        assertThat(auditCaptor.getValue().getOperationReason())
                .isEqualTo("原全宗 F000 -> 目标全宗 F001；纠正历史归属");
    }

    @Test
    @DisplayName("创建案卷时拒绝停用全宗")
    void createVolumeShouldRejectDisabledFonds() {
        when(archiveCategoryService.getCategory(1L)).thenReturn(volumeCategory());
        when(archiveMetadataReferenceService.getWritableFondsByCode("F001"))
                .thenThrow(new BadRequestException("全宗不可用"));

        assertThatThrownBy(
                        () ->
                                archiveVolumeService.createVolume(
                                        new CreateArchiveVolumeRequest(1L, "F001", "V-001", 2026),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("全宗不可用");

        verify(archiveMapper, never())
                .insertArchiveVolume(
                        anyString(), anyString(), anyString(), anyString(), any(), anyInt());
        verify(archiveMetadataReferenceService).getWritableFondsByCode("F001");
        verify(archiveMetadataReferenceService, never()).getFondsByCode("F001");
    }

    @Test
    @DisplayName("创建案卷时拒绝全宗未配置的分类")
    void createVolumeShouldRejectCategoryOutsideFondsScope() {
        when(archiveCategoryService.getCategory(1L)).thenReturn(volumeCategory());
        when(archiveMetadataReferenceService.getWritableFondsByCode("F001")).thenReturn(fonds());
        doThrow(new BadRequestException("该全宗未配置此分类", "categoryId", "该全宗未配置此分类"))
                .when(archiveCategoryService)
                .requireCategoryAvailableForFonds("F001", 1L);

        assertThatThrownBy(
                        () ->
                                archiveVolumeService.createVolume(
                                        new CreateArchiveVolumeRequest(1L, "F001", "V-001", 2026),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("该全宗未配置此分类");

        verify(archiveMapper, never())
                .insertArchiveVolume(
                        anyString(), anyString(), anyString(), anyString(), any(), anyInt());
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

    private ArchiveFondsDto fonds() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return activeFondsDto(1L, "F001", "测试全宗", now);
    }

    private Map<String, Object> itemRow() {
        return itemRow(true, "F000", false);
    }

    private void prepareItemUpdate(Map<String, Object> before, Map<String, Object> after) {
        when(archiveMapper.getArchiveItem(10L)).thenReturn(before, before, after);
        when(archiveCategoryService.listCategories(null)).thenReturn(List.of(itemCategory()));
        when(archiveMetadataService.listEffectiveFields(
                        eq(1L), eq(ArchiveLevel.ITEM), any(), any(), isNull()))
                .thenReturn(List.of());
        when(archiveMetadataService.listEnabledFields(1L, ArchiveLevel.ITEM)).thenReturn(List.of());
        when(archiveMetadataService.listEnabledFields(
                        1L,
                        ArchiveLevel.ITEM,
                        github.luckygc.am.module.archive.metadata.ArchiveFieldScope.PHYSICAL))
                .thenReturn(List.of());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
    }

    private Map<String, Object> itemRow(boolean inVolume, String fondsCode, boolean archived) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 10L);
        if (inVolume) row.put("volumeId", 20L);
        row.put("archiveLevel", ArchiveLevel.ITEM.value());
        row.put("fondsCode", fondsCode);
        row.put("fondsName", fondsCode.equals("F001") ? "测试全宗" : "原全宗");
        row.put("categoryCode", "contract");
        row.put("categoryName", "合同档案");
        row.put("archiveNo", "A-001");
        row.put("archiveYear", 2026);
        row.put("lockedFlag", false);
        if (archived) row.put("archivedAt", LocalDateTime.of(2026, 7, 1, 9, 0));
        return row;
    }
}
