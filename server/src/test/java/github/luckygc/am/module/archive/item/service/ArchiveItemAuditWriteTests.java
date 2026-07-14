package github.luckygc.am.module.archive.item.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemLockService.LockItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.CreateArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.DeleteItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.UpdateArchiveItemRequest;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFondsDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案条目操作审计写入")
class ArchiveItemAuditWriteTests {

    private ArchiveMapper archiveMapper;
    private ArchiveMetadataService archiveMetadataService;
    private ArchiveGovernanceService governanceService;
    private ArchiveItemSearchProjectionService searchProjectionService;
    private ArchiveItemAuditDataRepository auditRepository;
    private ArchiveItemRoutingService archiveItemRoutingService;
    private ArchiveItemLockService archiveItemLockService;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        archiveMetadataService = mock(ArchiveMetadataService.class);
        governanceService = mock(ArchiveGovernanceService.class);
        searchProjectionService = mock(ArchiveItemSearchProjectionService.class);
        auditRepository = mock(ArchiveItemAuditDataRepository.class);
        ArchiveDataScopeService dataScopeService = mock(ArchiveDataScopeService.class);
        when(dataScopeService.buildItemFilter(anyLong(), anyLong(), anyString()))
                .thenReturn(ArchiveDataScopeFilter.all());
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(anyLong(), anyString())).thenReturn(true);
        when(governanceService.requireDefaultVersionForNewArchive(anyString(), anyString()))
                .thenReturn(governanceVersion());
        archiveItemRoutingService =
                new ArchiveItemRoutingService(
                        archiveMetadataService,
                        governanceService,
                        archiveMapper,
                        searchProjectionService,
                        dataScopeService,
                        permissionService,
                        auditRepository);
        archiveItemLockService =
                new ArchiveItemLockService(
                        archiveMapper,
                        archiveItemRoutingService,
                        permissionService,
                        auditRepository);
    }

    @Test
    @DisplayName("创建档案条目后写入创建审计")
    void createItemShouldWriteAudit() {
        when(archiveMetadataService.getCategory(1L)).thenReturn(category());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataService.getEnabledFondsByCode("F001")).thenReturn(activeFonds());
        when(archiveMapper.countArchiveItemsByArchiveNo("contract", "A-001", null)).thenReturn(0);
        when(archiveMetadataService.listEnabledFields(eq(1L), eq(ArchiveLevel.ITEM)))
                .thenReturn(List.of());
        when(archiveMetadataService.listEnabledFields(eq(1L), eq(ArchiveLevel.ITEM), any()))
                .thenReturn(List.of());
        when(archiveMapper.insertArchiveItem(
                        anyString(),
                        isNull(),
                        eq("F001"),
                        eq("启用全宗"),
                        eq("contract"),
                        eq("合同档案"),
                        eq("A-001"),
                        eq("DRAFT"),
                        isNull(),
                        isNull(),
                        eq(2026),
                        eq(77L),
                        eq(9L)))
                .thenReturn(10L);
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow(false));

        archiveItemRoutingService.createItem(
                new CreateArchiveItemRequest(
                        1L, null, "F001", "A-001", 2026, "DRAFT", null, null, null, Map.of()),
                9L);

        verifyAudit("CREATE", null);
    }

    @Test
    @DisplayName("更新档案条目后写入修改审计")
    void updateItemShouldWriteAudit() {
        stubItemDetailLoad(false);
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataService.getEnabledFondsByCode("F001")).thenReturn(activeFonds());
        when(archiveMapper.countArchiveItemsByArchiveNo("contract", "A-002", 10L)).thenReturn(0);
        when(archiveMapper.updateArchiveItem(
                        eq(10L),
                        isNull(),
                        eq("F001"),
                        eq("启用全宗"),
                        eq("A-002"),
                        eq("DRAFT"),
                        isNull(),
                        isNull(),
                        eq(2026),
                        eq(9L)))
                .thenReturn(1);

        archiveItemRoutingService.updateItem(
                10L,
                new UpdateArchiveItemRequest(
                        null, "F001", "A-002", 2026, "DRAFT", null, null, null, Map.of()),
                9L);

        verifyAudit("UPDATE", null);
    }

    @Test
    @DisplayName("删除档案条目前写入删除审计")
    void deleteItemShouldWriteAudit() {
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow(false));
        when(archiveMetadataService.listCategories(null)).thenReturn(List.of(category()));
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMapper.markArchiveItemDeleted(10L, 9L)).thenReturn(1);

        archiveItemRoutingService.deleteItem(10L, 9L, new DeleteItemRequest("  清理重复件  "));

        verifyAudit("DELETE", "清理重复件");
    }

    @Test
    @DisplayName("锁定档案条目后写入锁定审计")
    void lockItemShouldWriteAudit() {
        when(archiveMapper.lockArchiveItem(10L, "借阅冻结", 9L)).thenReturn(1);
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow(true));
        when(archiveMetadataService.listCategories(null)).thenReturn(List.of(category()));

        archiveItemLockService.lockItem(10L, 9L, new LockItemRequest(" 借阅冻结 "));

        verifyAudit("LOCK", "借阅冻结");
    }

    @Test
    @DisplayName("解锁档案条目后写入解锁审计")
    void unlockItemShouldWriteAudit() {
        when(archiveMapper.unlockArchiveItem(10L, 9L)).thenReturn(1);
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow(false));
        when(archiveMetadataService.listCategories(null)).thenReturn(List.of(category()));

        archiveItemLockService.unlockItem(10L, 9L);

        verifyAudit("UNLOCK", null);
    }

    private void verifyAudit(String operationType, String operationReason) {
        ArgumentCaptor<ArchiveItemAudit> captor = ArgumentCaptor.forClass(ArchiveItemAudit.class);
        verify(auditRepository).insert(captor.capture());
        ArchiveItemAudit audit = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(audit.getSourceTableName())
                .isEqualTo("am_archive_item");
        org.assertj.core.api.Assertions.assertThat(audit.getSourceRecordId()).isEqualTo(10L);
        org.assertj.core.api.Assertions.assertThat(audit.getArchiveItemId()).isEqualTo(10L);
        org.assertj.core.api.Assertions.assertThat(audit.getFondsCode()).isEqualTo("F001");
        org.assertj.core.api.Assertions.assertThat(audit.getCategoryCode()).isEqualTo("contract");
        org.assertj.core.api.Assertions.assertThat(audit.getOperationType())
                .isEqualTo(operationType);
        org.assertj.core.api.Assertions.assertThat(audit.getOperationReason())
                .isEqualTo(operationReason);
        org.assertj.core.api.Assertions.assertThat(audit.getOperatedBy()).isEqualTo(9L);
    }

    private void stubItemDetailLoad(boolean locked) {
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow(locked));
        when(archiveMetadataService.listCategories(null)).thenReturn(List.of(category()));
        when(archiveMetadataService.listEffectiveFields(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(archiveMapper.loadDynamicRecord(anyString(), eq(10L))).thenReturn(Map.of());
    }

    private ArchiveFondsDto activeFonds() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return new ArchiveFondsDto(1L, "F001", "启用全宗", true, 0, now, now);
    }

    private ArchiveGovernanceSchemeVersion governanceVersion() {
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setId(77L);
        return version;
    }

    private ArchiveCategoryDto category() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return new ArchiveCategoryDto(
                1L,
                1L,
                null,
                "contract",
                "合同档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                "am_archive_item_contract",
                null,
                null,
                ArchiveTableStatus.BUILT,
                now,
                true,
                0,
                now,
                now);
    }

    private Map<String, Object> itemRow(boolean locked) {
        return Map.ofEntries(
                Map.entry("id", 10L),
                Map.entry("archiveLevel", ArchiveLevel.ITEM.value()),
                Map.entry("fondsCode", "F001"),
                Map.entry("fondsName", "启用全宗"),
                Map.entry("categoryCode", "contract"),
                Map.entry("categoryName", "合同档案"),
                Map.entry("archiveNo", "A-001"),
                Map.entry("electronicStatus", "DRAFT"),
                Map.entry("archiveYear", 2026),
                Map.entry("lockedFlag", locked));
    }
}
