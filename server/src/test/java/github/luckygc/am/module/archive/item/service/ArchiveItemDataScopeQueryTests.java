package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.ResolvedArchiveDataScope;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemRelationRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.CreateArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.SearchArchiveItemsRequest;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案查询数据范围")
class ArchiveItemDataScopeQueryTests {

    private ArchiveMapper archiveMapper;
    private ArchiveMetadataService archiveMetadataService;
    private ArchiveDataScopeService dataScopeService;
    private AuthorizationPermissionService permissionService;
    private ArchiveItemRoutingService archiveItemRoutingService;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        archiveMetadataService = mock(ArchiveMetadataService.class);
        ArchiveItemSearchProjectionService searchProjectionService =
                mock(ArchiveItemSearchProjectionService.class);
        dataScopeService = mock(ArchiveDataScopeService.class);
        permissionService = mock(AuthorizationPermissionService.class);
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
    }

    @Test
    @DisplayName("缺少读取功能权限时拒绝查询档案列表")
    void searchItemsShouldRejectMissingReadPermission() {
        when(permissionService.hasPermission(9L, "archive:item:read")).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.searchItems(
                                        new SearchArchiveItemsRequest(
                                                1L, null, null, null, null, null, null, null),
                                        9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("未指定分类时非任意数据范围不能读取总览列表")
    void listItemsShouldRejectOverviewWhenDataScopeIsNotAll() {
        when(dataScopeService.resolveUserDataScope(9L)).thenReturn(ResolvedArchiveDataScope.none());

        archiveItemRoutingService.listItems(null, null, 9L);

        verify(archiveMapper, org.mockito.Mockito.never()).listItemOverview();
    }

    @Test
    @DisplayName("查询档案列表时应用用户数据范围全宗条件")
    void searchItemsShouldApplyDataScopeFondsCodes() {
        when(archiveMetadataService.getCategory(1L)).thenReturn(category());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataService.listEffectiveFields(
                        eq(1L), eq(ArchiveLevel.ITEM), any(), eq(9L)))
                .thenReturn(List.of());
        when(archiveMetadataService.listUniqueConstraints(1L)).thenReturn(List.of());
        when(dataScopeService.buildItemFilter(9L, 1L, null))
                .thenReturn(ArchiveDataScopeFilter.fondsCodes(List.of("F001")));
        when(archiveMapper.listDynamicItems(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyBoolean(),
                        isNull(),
                        anyList(),
                        anyList(),
                        anyList(),
                        isNull(),
                        eq(9L),
                        eq(false),
                        anyString(),
                        nullable(String.class),
                        anyList(),
                        anyInt()))
                .thenReturn(List.of());

        archiveItemRoutingService.searchItems(
                new SearchArchiveItemsRequest(1L, null, null, null, null, null, null, null), 9L);

        verify(archiveMapper)
                .listDynamicItems(
                        eq("am_archive_item_contract"),
                        eq(""),
                        eq("ITEM"),
                        eq(false),
                        isNull(),
                        eq(
                                List.of(
                                        new ArchiveDataScopeSqlGroup(
                                                List.of("F001"), List.of(), List.of()))),
                        anyList(),
                        anyList(),
                        isNull(),
                        eq(9L),
                        eq(false),
                        anyString(),
                        nullable(String.class),
                        anyList(),
                        anyInt());
    }

    @Test
    @DisplayName("数据范围为空时不查询动态档案表")
    void searchItemsShouldReturnEmptyWhenDataScopeIsEmpty() {
        when(archiveMetadataService.getCategory(1L)).thenReturn(category());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataService.listEffectiveFields(
                        eq(1L), eq(ArchiveLevel.ITEM), any(), eq(9L)))
                .thenReturn(List.of());
        when(archiveMetadataService.listUniqueConstraints(1L)).thenReturn(List.of());
        when(dataScopeService.buildItemFilter(9L, 1L, null))
                .thenReturn(ArchiveDataScopeFilter.none());

        archiveItemRoutingService.searchItems(
                new SearchArchiveItemsRequest(1L, null, null, null, null, null, null, null), 9L);

        verify(archiveMapper, org.mockito.Mockito.never())
                .listDynamicItems(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyBoolean(),
                        any(),
                        anyList(),
                        anyList(),
                        anyList(),
                        any(),
                        any(),
                        anyBoolean(),
                        anyString(),
                        anyString(),
                        anyList(),
                        anyInt());
    }

    @Test
    @DisplayName("读取范围外档案详情时拒绝访问")
    void getItemDetailShouldRejectItemOutsideDataScope() {
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow());
        when(archiveMetadataService.listCategories(null)).thenReturn(List.of(category()));
        when(dataScopeService.buildItemFilter(9L, 1L, "F001"))
                .thenReturn(ArchiveDataScopeFilter.none());

        assertThatThrownBy(() -> archiveItemRoutingService.getItemDetail(10L, 9L, null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("读取档案关联时校验读取权限和来源档案数据范围")
    void listRelationsShouldRequireReadPermissionAndSourceDataScope() {
        when(permissionService.hasPermission(9L, "archive:item:read")).thenReturn(false);

        assertThatThrownBy(() -> archiveItemRoutingService.listRelations(10L, 1, 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(archiveMapper, never()).listItemRelations(10L);
    }

    @Test
    @DisplayName("创建档案关联时校验来源和目标档案数据范围")
    void createRelationShouldRequireSourceAndTargetDataScope() {
        when(archiveMapper.getArchiveItem(10L)).thenReturn(itemRow(10L, "F001", "A-001"));
        when(archiveMapper.getArchiveItem(11L)).thenReturn(itemRow(11L, "F002", "A-002"));
        when(archiveMetadataService.listCategories(null)).thenReturn(List.of(category()));
        when(dataScopeService.buildItemFilter(9L, 1L, "F001"))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(dataScopeService.buildItemFilter(9L, 1L, "F002"))
                .thenReturn(ArchiveDataScopeFilter.none());

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.createRelation(
                                        10L, new ArchiveItemRelationRequest(11L), 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(archiveMapper, never()).insertItemRelation(10L, 11L);
    }

    @Test
    @DisplayName("创建范围外档案时拒绝写入")
    void createItemShouldRejectTargetOutsideDataScope() {
        when(archiveMetadataService.getCategory(1L)).thenReturn(category());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMetadataService.getEnabledFondsByCode("F001"))
                .thenReturn(
                        new ArchiveMetadataService.ArchiveFondsDto(
                                1L,
                                "F001",
                                "启用全宗",
                                true,
                                0,
                                LocalDateTime.of(2026, 6, 30, 10, 0),
                                LocalDateTime.of(2026, 6, 30, 10, 0)));
        when(archiveMapper.countArchiveItemsByArchiveNo("contract", "A-001", null)).thenReturn(0);
        when(archiveMetadataService.listEnabledFields(eq(1L), eq(ArchiveLevel.ITEM)))
                .thenReturn(List.of());
        when(archiveMetadataService.listEnabledFields(eq(1L), eq(ArchiveLevel.ITEM), any()))
                .thenReturn(List.of());
        when(dataScopeService.buildItemFilter(9L, 1L, "F001"))
                .thenReturn(ArchiveDataScopeFilter.none());

        assertThatThrownBy(
                        () ->
                                archiveItemRoutingService.createItem(
                                        new CreateArchiveItemRequest(
                                                1L, null, "F001", "A-001", 2026, "DRAFT", null,
                                                null, null, null, Map.of()),
                                        9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(archiveMapper, org.mockito.Mockito.never())
                .insertArchiveItem(
                        anyString(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        any(),
                        anyInt(),
                        anyLong());
    }

    private ArchiveCategoryDto category() {
        return new ArchiveCategoryDto(
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
                LocalDateTime.of(2026, 6, 30, 10, 0),
                true,
                0,
                LocalDateTime.of(2026, 6, 30, 10, 0),
                LocalDateTime.of(2026, 6, 30, 10, 0));
    }

    private Map<String, Object> itemRow() {
        return itemRow(10L, "F001", "A-001");
    }

    private Map<String, Object> itemRow(Long id, String fondsCode, String archiveNo) {
        return Map.ofEntries(
                Map.entry("id", id),
                Map.entry("fonds_code", fondsCode),
                Map.entry("fonds_name", "启用全宗"),
                Map.entry("category_code", "contract"),
                Map.entry("category_name", "合同档案"),
                Map.entry("archive_no", archiveNo),
                Map.entry("electronic_status", "DRAFT"),
                Map.entry("archive_year", 2026),
                Map.entry("created_at", LocalDateTime.of(2026, 6, 30, 10, 0)),
                Map.entry("locked_flag", false));
    }
}
