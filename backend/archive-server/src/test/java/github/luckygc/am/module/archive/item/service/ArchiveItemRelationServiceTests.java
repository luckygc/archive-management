package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService.ArchiveItemRelationRequest;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案关系服务")
class ArchiveItemRelationServiceTests {

    private ArchiveMapper archiveMapper;
    private ArchiveItemReadService archiveItemReadService;
    private ArchiveDataScopeService dataScopeService;
    private ArchiveCategoryService archiveCategoryService;
    private AuthorizationPermissionService permissionService;
    private ArchiveItemRelationService service;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        archiveItemReadService = mock(ArchiveItemReadService.class);
        dataScopeService = mock(ArchiveDataScopeService.class);
        archiveCategoryService = mock(ArchiveCategoryService.class);
        permissionService = mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(anyLong(), anyString())).thenReturn(true);
        when(archiveCategoryService.listCategories(null))
                .thenReturn(List.of(category(1L, "contract")));
        when(dataScopeService.buildItemFilter(9L, 1L, null))
                .thenReturn(ArchiveDataScopeFilter.all());
        service =
                new ArchiveItemRelationService(
                        archiveMapper,
                        archiveItemReadService,
                        dataScopeService,
                        archiveCategoryService,
                        permissionService);
    }

    @Test
    @DisplayName("关系列表在同一 Mapper 分页查询中处理另一端和目标数据范围")
    void listRelationsShouldFilterRelatedEndpointInsidePagedMapperQuery() {
        ArchiveDataScopeSqlGroup group =
                new ArchiveDataScopeSqlGroup(List.of("F001"), List.of(), List.of());
        when(archiveCategoryService.listCategories(null))
                .thenReturn(List.of(category(1L, "contract"), category(2L, "project")));
        when(dataScopeService.buildItemFilter(9L, 1L, null))
                .thenReturn(ArchiveDataScopeFilter.none());
        when(dataScopeService.buildItemFilter(9L, 2L, null))
                .thenReturn(ArchiveDataScopeFilter.groups(List.of(group)));
        when(archiveMapper.listItemRelations(anyLong(), any(), any()))
                .thenReturn(List.of(incomingRow(1L)));

        var response = service.listRelations(10L, 1, PageRequest.ofSize(100), 9L);

        assertThat(response.items())
                .singleElement()
                .satisfies(
                        relation -> {
                            assertThat(relation.sourceItemId()).isEqualTo(11L);
                            assertThat(relation.targetItemId()).isEqualTo(10L);
                            assertThat(relation.relatedItem().itemId()).isEqualTo(11L);
                            assertThat(relation.direction()).isEqualTo("INCOMING");
                        });
        verify(archiveMapper)
                .listItemRelations(
                        eq(10L),
                        argThat(
                                criteria ->
                                        !criteria.allData()
                                                && criteria.targetScopes().size() == 1
                                                && criteria.targetScopes()
                                                        .getFirst()
                                                        .categoryCode()
                                                        .equals("project")
                                                && criteria.targetScopes()
                                                        .getFirst()
                                                        .groups()
                                                        .equals(List.of(group))),
                        argThat(page -> page.rowLimit() == 101 && !page.previous()));
        verify(archiveItemReadService, never()).assertItemInDataScope(eq(11L), eq(9L));
    }

    @Test
    @DisplayName("关系列表按 id 升序使用 pageSize+1 生成下一页")
    void listRelationsShouldBuildNextCursorFromPageSizePlusOne() {
        when(archiveMapper.listItemRelations(anyLong(), any(), any()))
                .thenReturn(List.of(outgoingRow(1L), outgoingRow(2L), outgoingRow(3L)));

        CursorPageResponse<?> response = service.listRelations(10L, 1, PageRequest.ofSize(2), 9L);

        assertThat(response.items()).extracting("id").containsExactly(1L, 2L);
        var page = (CursorPageResponse.DefaultCursorPageResponse<?>) response;
        assertThat(page.prevValues()).isNull();
        assertThat(page.nextValues()).isEqualTo(List.of(2L));
    }

    @Test
    @DisplayName("向前翻页时反转查询结果并正确生成前后游标")
    void listRelationsShouldBuildBothCursorsForPreviousPage() {
        when(archiveMapper.listItemRelations(anyLong(), any(), any()))
                .thenReturn(List.of(outgoingRow(3L), outgoingRow(2L), outgoingRow(1L)));
        PageRequest request = PageRequest.ofSize(2).beforeCursor(PageRequest.Cursor.forKey(4L));

        CursorPageResponse<?> response = service.listRelations(10L, 1, request, 9L);

        assertThat(response.items()).extracting("id").containsExactly(2L, 3L);
        var page = (CursorPageResponse.DefaultCursorPageResponse<?>) response;
        assertThat(page.prevValues()).isEqualTo(List.of(2L));
        assertThat(page.nextValues()).isEqualTo(List.of(3L));
        verify(archiveMapper)
                .listItemRelations(
                        eq(10L),
                        any(),
                        argThat(
                                window ->
                                        window.previous()
                                                && Long.valueOf(4L).equals(window.cursorId())
                                                && window.rowLimit() == 3));
    }

    @Test
    @DisplayName("重复关系返回 409 冲突")
    void createRelationShouldReturnConflictForDuplicatePair() {
        when(archiveMapper.insertItemRelation(10L, 11L))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(
                        () -> service.createRelation(10L, new ArchiveItemRelationRequest(11L), 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("自关联返回 targetItemId 字段错误")
    void createRelationShouldRejectSelfRelation() {
        assertThatThrownBy(
                        () -> service.createRelation(10L, new ArchiveItemRelationRequest(10L), 9L))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .singleElement()
                                        .satisfies(
                                                violation ->
                                                        assertThat(violation.field())
                                                                .isEqualTo("targetItemId")));
        verify(archiveMapper, never()).insertItemRelation(anyLong(), anyLong());
    }

    @Test
    @DisplayName("删除关系允许当前档案位于目标端并校验另一端数据范围")
    void deleteRelationShouldAcceptCurrentItemAsTargetEndpoint() {
        when(archiveMapper.getItemRelation(7L, 10L)).thenReturn(incomingRow(7L));
        when(archiveMapper.deleteItemRelation(7L, 10L)).thenReturn(1);

        service.deleteRelation(10L, 7L, 9L);

        verify(archiveItemReadService).assertItemInDataScope(10L, 9L);
        verify(archiveItemReadService).assertItemInDataScope(11L, 9L);
        verify(archiveMapper).deleteItemRelation(7L, 10L);
    }

    @Test
    @DisplayName("关系路径不包含指定关系时返回 404 且不删除")
    void deleteRelationShouldRejectPathMismatch() {
        when(archiveMapper.getItemRelation(7L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteRelation(10L, 7L, 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(archiveMapper, never()).deleteItemRelation(anyLong(), anyLong());
    }

    private Map<String, Object> outgoingRow(Long id) {
        return relationRow(id, 10L, 11L, 11L, "OUTGOING", 3L);
    }

    private Map<String, Object> incomingRow(Long id) {
        return relationRow(id, 11L, 10L, 11L, "INCOMING", 1L);
    }

    private Map<String, Object> relationRow(
            Long id, Long sourceId, Long targetId, Long relatedId, String direction, Long total) {
        return Map.ofEntries(
                Map.entry("id", id),
                Map.entry("total", total),
                Map.entry("sourceItemId", sourceId),
                Map.entry("targetItemId", targetId),
                Map.entry("relatedItemId", relatedId),
                Map.entry("direction", direction),
                Map.entry("createdAt", LocalDateTime.of(2026, 7, 15, 10, 0)),
                Map.entry("relatedFondsCode", "F001"),
                Map.entry("relatedFondsName", "默认全宗"),
                Map.entry("relatedCategoryCode", "project"),
                Map.entry("relatedCategoryName", "项目档案"),
                Map.entry("relatedArchiveNo", "A-2026-002"));
    }

    private ArchiveCategoryDto category(Long id, String code) {
        return new ArchiveCategoryDto(
                id,
                1L,
                null,
                code,
                code + "档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                "am_archive_item_data_" + code,
                null,
                null,
                ArchiveTableStatus.BUILT,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                true,
                0,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                LocalDateTime.of(2026, 7, 1, 10, 0));
    }
}
