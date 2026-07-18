package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.ArchiveItemLineRowResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.ArchiveItemLineTableDefinitionResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.CreateArchiveItemLineRowRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineRowService.PatchArchiveItemLineRowRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowCommands.ArchiveItemLineRowDeleteCommand;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowCommands.ArchiveItemLineRowInsertCommand;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowCommands.ArchiveItemLineRowPageQuery;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowCommands.ArchiveItemLineRowUpdateCommand;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案明细行服务")
class ArchiveItemLineRowServiceTests {

    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ArchiveItemReadService readService = mock(ArchiveItemReadService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveItemFieldValueConverter converter = new ArchiveItemFieldValueConverter();
    private final ArchiveItemLineRowService service =
            new ArchiveItemLineRowService(archiveMapper, readService, permissionService, converter);

    @Test
    @DisplayName("数据范围不足时读取在加载明细定义前停止")
    void listRowsShouldStopWhenItemIsOutsideDataScope() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足"))
                .when(readService)
                .assertItemInDataScope(3L, 8L);

        assertThatThrownBy(() -> service.listRows(3L, 4L, PageRequest.ofSize(10), 8L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(archiveMapper, never()).getItemLineTable(4L);
    }

    @Test
    @DisplayName("读取权限不足时在数据范围和动态表访问前停止")
    void listRowsShouldStopWhenReadPermissionIsMissing() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足"))
                .when(permissionService)
                .requirePermission(8L, AuthorizationPermissionCode.ARCHIVE_ITEM_READ);

        assertThatThrownBy(() -> service.listRows(3L, 4L, PageRequest.ofSize(10), 8L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(readService, never()).assertItemInDataScope(3L, 8L);
        verify(archiveMapper, never()).getItemLineTable(4L);
    }

    @Test
    @DisplayName("档案锁定时写入在动态表访问前停止")
    void createRowShouldStopWhenItemIsLocked() {
        doThrow(new BadRequestException("档案条目已锁定，不能修改")).when(readService).ensureItemEditable(3L);

        assertThatThrownBy(
                        () ->
                                service.createRow(
                                        3L,
                                        4L,
                                        new CreateArchiveItemLineRowRequest(0, Map.of()),
                                        8L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("已锁定");

        verify(archiveMapper, never()).getItemLineTable(4L);
    }

    @Test
    @DisplayName("PATCH 路径中的行不属于当前档案时返回不存在")
    void patchRowShouldRejectRowOutsidePath() {
        stubBuiltTable(false);
        when(archiveMapper.getItemLineRow(any())).thenReturn(null);

        assertThatThrownBy(
                        () ->
                                service.patchRow(
                                        3L,
                                        4L,
                                        9L,
                                        new PatchArchiveItemLineRowRequest(
                                                true, 1, false, Map.of()),
                                        8L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(archiveMapper, never()).updateItemLineRow(any());
    }

    @Test
    @DisplayName("条目范围定义入口仅返回当前分类已启用且已构建的明细表")
    void listLineTablesShouldBeItemScopedAndExcludeUnbuiltTables() {
        when(readService.getItem(3L)).thenReturn(item());
        when(readService.getCategoryByCode("contract")).thenReturn(category());
        when(archiveMapper.listItemLineTables(7L))
                .thenReturn(List.of(lineTable(false), lineTableRow(5L, "unbuilt_table")));
        when(archiveMapper.listItemLineFields(4L))
                .thenReturn(List.of(field("party_name", "单位名称", "TEXT", "f_party_name")));
        when(archiveMapper.tableExists("am_archive_item_line_contract_party")).thenReturn(1);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_party_name"))
                .thenReturn(1);
        when(archiveMapper.tableExists("unbuilt_table")).thenReturn(0);

        List<ArchiveItemLineTableDefinitionResponse> definitions = service.listLineTables(3L, 8L);

        verify(permissionService)
                .requirePermission(8L, AuthorizationPermissionCode.ARCHIVE_ITEM_READ);
        verify(readService).assertItemInDataScope(3L, 8L);
        assertThat(definitions)
                .extracting(ArchiveItemLineTableDefinitionResponse::id)
                .containsExactly(4L);
        assertThat(definitions.getFirst().fields())
                .extracting("fieldCode")
                .containsExactly("party_name");
    }

    @Test
    @DisplayName("条目范围定义不暴露存在未物化启用列的明细表")
    void listLineTablesShouldExcludePartiallyBuiltTable() {
        when(readService.getItem(3L)).thenReturn(item());
        when(readService.getCategoryByCode("contract")).thenReturn(category());
        when(archiveMapper.listItemLineTables(7L)).thenReturn(List.of(lineTable(false)));
        when(archiveMapper.listItemLineFields(4L))
                .thenReturn(
                        List.of(
                                field("party_name", "单位名称", "TEXT", "f_party_name"),
                                field("amount", "金额", "DECIMAL", "f_amount")));
        when(archiveMapper.tableExists("am_archive_item_line_contract_party")).thenReturn(1);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_party_name"))
                .thenReturn(1);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_amount"))
                .thenReturn(0);

        assertThat(service.listLineTables(3L, 8L)).isEmpty();
    }

    @Test
    @DisplayName("列表要求读取权限和数据范围并按正向游标多取一行")
    void listRowsShouldEnforceReadScopeAndBuildForwardPage() {
        stubBuiltTable(false);
        when(archiveMapper.listItemLineRows(any(ArchiveItemLineRowPageQuery.class)))
                .thenReturn(
                        List.of(row(9L, 0, "甲", 3L), row(10L, 0, "乙", 3L), row(11L, 1, "丙", 3L)));

        CursorPageResponse<ArchiveItemLineRowResponse> response =
                service.listRows(3L, 4L, CursorPageTokenCodec.pageRequest(2, null, true), 8L);

        verify(permissionService)
                .requirePermission(8L, AuthorizationPermissionCode.ARCHIVE_ITEM_READ);
        verify(readService).assertItemInDataScope(3L, 8L);
        ArgumentCaptor<ArchiveItemLineRowPageQuery> captor =
                ArgumentCaptor.forClass(ArchiveItemLineRowPageQuery.class);
        verify(archiveMapper).listItemLineRows(captor.capture());
        assertThat(captor.getValue().rowLimit()).isEqualTo(3);
        assertThat(captor.getValue().requestTotal()).isTrue();
        assertThat(captor.getValue().previous()).isFalse();
        assertThat(captor.getValue().selectColumns()).containsExactly("f_party_name", "f_amount");
        assertThat(response.items())
                .extracting(ArchiveItemLineRowResponse::id)
                .containsExactly(9L, 10L);
        assertThat(((CursorPageResponse.DefaultCursorPageResponse<?>) response).nextValues())
                .isEqualTo(List.<Object>of(0, 10L));
        assertThat(response.total()).isEqualTo(3L);
    }

    @Test
    @DisplayName("反向游标查询恢复为正序并生成上一页游标")
    void listRowsShouldReversePreviousQueryResults() {
        stubBuiltTable(false);
        when(archiveMapper.listItemLineRows(any(ArchiveItemLineRowPageQuery.class)))
                .thenReturn(List.of(row(8L, 0, "乙"), row(7L, 0, "甲")));
        PageRequest request = PageRequest.ofSize(1).beforeCursor(PageRequest.Cursor.forKey(0, 9L));

        CursorPageResponse<ArchiveItemLineRowResponse> response =
                service.listRows(3L, 4L, request, 8L);

        assertThat(response.items()).extracting(ArchiveItemLineRowResponse::id).containsExactly(8L);
        assertThat(((CursorPageResponse.DefaultCursorPageResponse<?>) response).prevValues())
                .isEqualTo(List.<Object>of(0, 8L));
    }

    @Test
    @DisplayName("创建校验更新权限、数据范围、可编辑状态并映射字段白名单")
    void createRowShouldValidateBoundaryAndConvertValues() {
        stubBuiltTable(false);
        when(archiveMapper.insertItemLineRow(any(ArchiveItemLineRowInsertCommand.class)))
                .thenReturn(9L);
        when(archiveMapper.getItemLineRow(any())).thenReturn(row(9L, 0, "甲"));

        ArchiveItemLineRowResponse response =
                service.createRow(
                        3L,
                        4L,
                        new CreateArchiveItemLineRowRequest(0, Map.of("party_name", "甲")),
                        8L);

        verify(permissionService)
                .requirePermission(8L, AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE);
        verify(readService).assertItemInDataScope(3L, 8L);
        verify(readService).ensureItemEditable(3L);
        ArgumentCaptor<ArchiveItemLineRowInsertCommand> captor =
                ArgumentCaptor.forClass(ArchiveItemLineRowInsertCommand.class);
        verify(archiveMapper).insertItemLineRow(captor.capture());
        assertThat(captor.getValue().assignments())
                .singleElement()
                .satisfies(
                        assignment -> {
                            assertThat(assignment.columnName()).isEqualTo("f_party_name");
                            assertThat(assignment.value()).isEqualTo("甲");
                        });
        assertThat(response.id()).isEqualTo(9L);
    }

    @Test
    @DisplayName("PATCH 仅更新出现的值并保留显式 null")
    void patchRowShouldPreserveMissingValuesAndClearExplicitNull() {
        stubBuiltTable(false);
        when(archiveMapper.getItemLineRow(any())).thenReturn(row(9L, 0, "甲"));
        when(archiveMapper.updateItemLineRow(any())).thenReturn(1);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("amount", "12.50");
        values.put("party_name", null);

        service.patchRow(
                3L, 4L, 9L, new PatchArchiveItemLineRowRequest(false, null, true, values), 8L);

        ArgumentCaptor<ArchiveItemLineRowUpdateCommand> captor =
                ArgumentCaptor.forClass(ArchiveItemLineRowUpdateCommand.class);
        verify(archiveMapper).updateItemLineRow(captor.capture());
        assertThat(captor.getValue().lineOrderPresent()).isFalse();
        assertThat(captor.getValue().assignments())
                .extracting(ArchiveSqlAssignment::columnName, ArchiveSqlAssignment::value)
                .containsExactlyInAnyOrder(
                        tuple("f_amount", new BigDecimal("12.50")), tuple("f_party_name", null));
    }

    @Test
    @DisplayName("PATCH 未包含任何更新字段时不生成 UPDATE")
    void patchRowShouldSkipEmptyUpdate() {
        stubBuiltTable(false);
        when(archiveMapper.getItemLineRow(any())).thenReturn(row(9L, 0, "甲"));

        ArchiveItemLineRowResponse response =
                service.patchRow(
                        3L,
                        4L,
                        9L,
                        new PatchArchiveItemLineRowRequest(false, null, false, Map.of()),
                        8L);

        verify(archiveMapper, never()).updateItemLineRow(any());
        assertThat(response.id()).isEqualTo(9L);
    }

    @Test
    @DisplayName("未知字段返回 values.fieldCode 字段错误")
    void createRowShouldRejectUnknownFieldWithStablePath() {
        stubBuiltTable(false);

        assertThatThrownBy(
                        () ->
                                service.createRow(
                                        3L,
                                        4L,
                                        new CreateArchiveItemLineRowRequest(
                                                0, Map.of("unknown", "x")),
                                        8L))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .extracting("field")
                                        .containsExactly("values.unknown"));

        verify(archiveMapper, never()).insertItemLineRow(any());
    }

    @Test
    @DisplayName("明细表不属于档案分类时拒绝访问")
    void listRowsShouldRejectMismatchedCategory() {
        stubBuiltTable(true);

        assertThatThrownBy(() -> service.listRows(3L, 4L, PageRequest.ofSize(10), 8L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(archiveMapper, never()).listItemLineRows(any(ArchiveItemLineRowPageQuery.class));
    }

    @Test
    @DisplayName("物理表未构建时拒绝访问")
    void listRowsShouldRejectUnbuiltTable() {
        stubBuiltTable(false);
        when(archiveMapper.tableExists("am_archive_item_line_contract_party")).thenReturn(0);

        assertThatThrownBy(() -> service.listRows(3L, 4L, PageRequest.ofSize(10), 8L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("未构建");
    }

    @Test
    @DisplayName("明细表存在但启用字段列尚未物化时在动态查询前拒绝访问")
    void listRowsShouldRejectPartiallyBuiltTable() {
        stubBuiltTable(false);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_amount"))
                .thenReturn(0);

        assertThatThrownBy(() -> service.listRows(3L, 4L, PageRequest.ofSize(10), 8L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("未构建");

        verify(archiveMapper, never()).listItemLineRows(any(ArchiveItemLineRowPageQuery.class));
    }

    @Test
    @DisplayName("删除使用路径限定并写入删除人")
    void deleteRowShouldUsePathScopedLogicalDelete() {
        stubBuiltTable(false);
        when(archiveMapper.getItemLineRow(any())).thenReturn(row(9L, 0, "甲"));
        when(archiveMapper.deleteItemLineRow(any())).thenReturn(1);

        service.deleteRow(3L, 4L, 9L, 8L);

        ArgumentCaptor<ArchiveItemLineRowDeleteCommand> captor =
                ArgumentCaptor.forClass(ArchiveItemLineRowDeleteCommand.class);
        verify(archiveMapper).deleteItemLineRow(captor.capture());
        assertThat(captor.getValue().itemId()).isEqualTo(3L);
        assertThat(captor.getValue().rowId()).isEqualTo(9L);
        assertThat(captor.getValue().userId()).isEqualTo(8L);
    }

    private void stubBuiltTable(boolean mismatchedCategory) {
        when(readService.getItem(3L)).thenReturn(item());
        when(archiveMapper.getItemLineTable(4L)).thenReturn(lineTable(mismatchedCategory));
        when(archiveMapper.listItemLineFields(4L))
                .thenReturn(
                        List.of(
                                field("party_name", "单位名称", "TEXT", "f_party_name"),
                                field("amount", "金额", "DECIMAL", "f_amount")));
        when(archiveMapper.tableExists("am_archive_item_line_contract_party")).thenReturn(1);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_party_name"))
                .thenReturn(1);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_amount"))
                .thenReturn(1);
    }

    private static ArchiveItemDto item() {
        return new ArchiveItemDto(
                3L,
                null,
                "F001",
                "默认全宗",
                "contract",
                "合同档案",
                "A-001",
                "DRAFT",
                null,
                null,
                2026,
                11L,
                false,
                null,
                null,
                null);
    }

    private static Map<String, Object> lineTable(boolean mismatchedCategory) {
        return Map.of(
                "id",
                4L,
                "categoryId",
                mismatchedCategory ? 8L : 7L,
                "categoryCode",
                mismatchedCategory ? "other" : "contract",
                "tableCode",
                "contract_party",
                "tableName",
                "合同方",
                "physicalTableName",
                "am_archive_item_line_contract_party",
                "sortOrder",
                1,
                "enabled",
                true);
    }

    private static Map<String, Object> lineTableRow(Long id, String physicalTableName) {
        return Map.of(
                "id",
                id,
                "categoryId",
                7L,
                "tableCode",
                "unbuilt",
                "tableName",
                "未构建",
                "physicalTableName",
                physicalTableName,
                "sortOrder",
                2,
                "enabled",
                true);
    }

    private static ArchiveCategoryDto category() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 9, 0);
        return new ArchiveCategoryDto(
                7L,
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
                1,
                now,
                now);
    }

    private static Map<String, Object> field(String code, String name, String type, String column) {
        return Map.of(
                "id",
                code.equals("amount") ? 22L : 21L,
                "lineTableId",
                4L,
                "fieldCode",
                code,
                "fieldName",
                name,
                "fieldType",
                type,
                "columnName",
                column,
                "exactSearchable",
                false,
                "sortOrder",
                1,
                "enabled",
                true);
    }

    private static Map<String, Object> row(Long id, int lineOrder, String partyName) {
        return row(id, lineOrder, partyName, null);
    }

    private static Map<String, Object> row(Long id, int lineOrder, String partyName, Long total) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("itemId", 3L);
        row.put("lineOrder", lineOrder);
        row.put("f_party_name", partyName);
        row.put("f_amount", new BigDecimal("12.50"));
        if (total != null) {
            row.put("total", total);
        }
        return row;
    }
}
