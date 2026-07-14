package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemSource;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案查询工作台摘要")
class ArchiveItemQueryWorkspaceTests {

    private final ArchiveMetadataService metadataService = mock(ArchiveMetadataService.class);
    private final ArchiveCategoryService categoryService = mock(ArchiveCategoryService.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ArchiveDataScopeService dataScopeService = mock(ArchiveDataScopeService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveItemSearchCriteriaCompiler criteriaCompiler =
            mock(ArchiveItemSearchCriteriaCompiler.class);
    private final ArchiveItemCursorPageAssembler pageAssembler =
            mock(ArchiveItemCursorPageAssembler.class);
    private final ArchiveItemQueryService service =
            new ArchiveItemQueryService(
                    metadataService,
                    categoryService,
                    archiveMapper,
                    dataScopeService,
                    permissionService,
                    criteriaCompiler,
                    pageAssembler);

    @Test
    @DisplayName("分类摘要复用动态表 source、数据范围条件和未删除语义")
    void summaryReusesDynamicSourceAndResolvedScope() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(true);
        when(categoryService.getCategory(7L)).thenReturn(category("am_archive_item_data_contract"));
        when(archiveMapper.tableExists("am_archive_item_data_contract")).thenReturn(1);
        ArchiveDataScopeSqlGroup group =
                new ArchiveDataScopeSqlGroup(List.of("F001"), List.of(), List.of(), List.of());
        when(dataScopeService.buildItemFilter(8L, 7L, null))
                .thenReturn(ArchiveDataScopeFilter.groups(List.of(group)));
        when(archiveMapper.summarizeDynamicItems(any(), any()))
                .thenReturn(
                        Map.of(
                                "archive_item_count", 7L,
                                "draft_count", 2L,
                                "locked_count", 1L,
                                "electronic_file_count", 4L));

        ArchiveWorkspaceCategorySummary summary = service.summarizeCategoryForWorkspace(7L, 8L);

        ArgumentCaptor<ArchiveDynamicItemSource> source =
                ArgumentCaptor.forClass(ArchiveDynamicItemSource.class);
        ArgumentCaptor<ArchiveDynamicItemCriteria> criteria =
                ArgumentCaptor.forClass(ArchiveDynamicItemCriteria.class);
        verify(archiveMapper).summarizeDynamicItems(source.capture(), criteria.capture());
        assertThat(source.getValue())
                .isEqualTo(new ArchiveDynamicItemSource("am_archive_item_data_contract", false));
        assertThat(criteria.getValue().dataScopeGroups()).containsExactly(group);
        assertThat(criteria.getValue().conditions()).isEmpty();
        assertThat(criteria.getValue().relatedGroups()).isEmpty();
        assertThat(criteria.getValue().fullTextKeyword()).isNull();
        assertThat(summary).isEqualTo(new ArchiveWorkspaceCategorySummary(7, 2, 1, 4));
    }

    @Test
    @DisplayName("公开分类摘要能力自身拒绝无读取权限用户")
    void publicSummaryCapabilityRequiresReadPermission() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(false);

        assertThatThrownBy(() -> service.summarizeCategoryForWorkspace(7L, 8L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(categoryService, never()).getCategory(7L);
        verify(archiveMapper, never()).summarizeDynamicItems(any(), any());
    }

    @Test
    @DisplayName("空数据范围不执行 Mapper 并返回全零")
    void emptyScopeSkipsMapper() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(true);
        when(categoryService.getCategory(7L)).thenReturn(category("am_archive_item_data_contract"));
        when(archiveMapper.tableExists("am_archive_item_data_contract")).thenReturn(1);
        when(dataScopeService.buildItemFilter(8L, 7L, null))
                .thenReturn(ArchiveDataScopeFilter.none());

        assertThat(service.summarizeCategoryForWorkspace(7L, 8L))
                .isEqualTo(ArchiveWorkspaceCategorySummary.empty());

        verify(archiveMapper, never()).summarizeDynamicItems(any(), any());
    }

    @Test
    @DisplayName("未构建动态表保持档案查询的 412 语义")
    void unbuiltDynamicTableKeepsPreconditionSemantics() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(true);
        when(categoryService.getCategory(7L)).thenReturn(category("am_archive_item_data_contract"));
        when(archiveMapper.tableExists("am_archive_item_data_contract")).thenReturn(0);

        assertThatThrownBy(() -> service.summarizeCategoryForWorkspace(7L, 8L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.PRECONDITION_FAILED));

        verify(dataScopeService, never()).buildItemFilter(8L, 7L, null);
        verify(archiveMapper, never()).summarizeDynamicItems(any(), any());
    }

    @Test
    @DisplayName("聚合结果 null 明确解释为零")
    void nullAggregateValuesBecomeZero() {
        stubAllDataCategory();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("archive_item_count", null);
        row.put("draft_count", 2L);
        row.put("locked_count", null);
        row.put("electronic_file_count", 4L);
        when(archiveMapper.summarizeDynamicItems(any(), any())).thenReturn(row);

        assertThat(service.summarizeCategoryForWorkspace(7L, 8L))
                .isEqualTo(new ArchiveWorkspaceCategorySummary(0, 2, 0, 4));
    }

    @Test
    @DisplayName("超出 long 的聚合结果显式失败而不截断")
    void aggregateOverflowFailsExplicitly() {
        stubAllDataCategory();
        when(archiveMapper.summarizeDynamicItems(any(), any()))
                .thenReturn(
                        Map.of(
                                "archive_item_count",
                                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                                "draft_count",
                                0L,
                                "locked_count",
                                0L,
                                "electronic_file_count",
                                0L));

        assertThatThrownBy(() -> service.summarizeCategoryForWorkspace(7L, 8L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archiveItemCount");
    }

    @Test
    @DisplayName("动态表标识符在进入 Mapper 前校验")
    void invalidDynamicTableIdentifierIsRejectedBeforeMapper() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(true);
        when(categoryService.getCategory(7L)).thenReturn(category("bad-table"));

        assertThatThrownBy(() -> service.summarizeCategoryForWorkspace(7L, 8L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("动态表名非法");

        verify(archiveMapper, never()).tableExists(any());
    }

    private void stubAllDataCategory() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(true);
        when(categoryService.getCategory(7L)).thenReturn(category("am_archive_item_data_contract"));
        when(archiveMapper.tableExists("am_archive_item_data_contract")).thenReturn(1);
        when(dataScopeService.buildItemFilter(8L, 7L, null))
                .thenReturn(ArchiveDataScopeFilter.all());
    }

    private ArchiveCategoryDto category(String tableName) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 9, 0);
        return new ArchiveCategoryDto(
                7L,
                1L,
                null,
                "contract",
                "合同档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                tableName,
                null,
                null,
                ArchiveTableStatus.BUILT,
                now,
                true,
                0,
                now,
                now);
    }
}
