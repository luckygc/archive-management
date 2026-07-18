package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案明细表服务")
class ArchiveItemLineTableServiceTests {

    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ArchiveItemLineTableService service =
            new ArchiveItemLineTableService(
                    archiveMapper,
                    mock(ArchiveMetadataService.class),
                    mock(ArchiveCategoryService.class),
                    mock(AuthorizationPermissionService.class));

    @Test
    @DisplayName("物理表和字段列均已存在时不执行任何 DDL")
    void buildExistingTableShouldNotChangeExistingColumns() {
        when(archiveMapper.getItemLineTable(12L)).thenReturn(lineTable());
        when(archiveMapper.listItemLineFields(12L))
                .thenReturn(List.of(lineField(21L, "party_name", "f_party_name", "TEXT")));
        when(archiveMapper.tableExists("am_archive_item_line_contract_party")).thenReturn(1);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_party_name"))
                .thenReturn(1);

        service.buildLineTable(12L, 9L);

        verify(archiveMapper, never()).executeSql(anyString());
    }

    @Test
    @DisplayName("物理表已存在时精确补充后来新增的字段列")
    void buildExistingTableShouldAddMissingColumn() {
        when(archiveMapper.getItemLineTable(12L)).thenReturn(lineTable());
        when(archiveMapper.listItemLineFields(12L))
                .thenReturn(List.of(lineField(22L, "amount", "f_amount", "DECIMAL")));
        when(archiveMapper.tableExists("am_archive_item_line_contract_party")).thenReturn(1);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_amount"))
                .thenReturn(0);

        service.buildLineTable(12L, 9L);

        verify(archiveMapper)
                .executeSql(
                        "alter table am_archive_item_line_contract_party add column f_amount numeric");
    }

    @Test
    @DisplayName("任一字段列名包含非法字符时在 schema 探测前失败")
    void buildExistingTableShouldRejectInvalidColumnName() {
        when(archiveMapper.getItemLineTable(12L)).thenReturn(lineTable());
        when(archiveMapper.listItemLineFields(12L))
                .thenReturn(
                        List.of(
                                lineField(21L, "party_name", "f_party_name", "TEXT"),
                                lineField(22L, "bad", "bad-column", "TEXT")));

        assertThatThrownBy(() -> service.buildLineTable(12L, 9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("字段列名非法");

        verifyNoSchemaAccess();
    }

    @Test
    @DisplayName("超长物理表名在 schema 探测前失败")
    void buildLineTableShouldRejectOverlongPhysicalTableNameBeforeSchemaAccess() {
        when(archiveMapper.getItemLineTable(12L)).thenReturn(lineTable("a".repeat(64)));
        when(archiveMapper.listItemLineFields(12L))
                .thenReturn(List.of(lineField(21L, "party_name", "f_party_name", "TEXT")));

        assertThatThrownBy(() -> service.buildLineTable(12L, 9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("动态明细表名非法");

        verifyNoSchemaAccess();
    }

    @Test
    @DisplayName("超长字段列名在 schema 探测前失败")
    void buildLineTableShouldRejectOverlongColumnNameBeforeSchemaAccess() {
        when(archiveMapper.getItemLineTable(12L)).thenReturn(lineTable());
        when(archiveMapper.listItemLineFields(12L))
                .thenReturn(List.of(lineField(21L, "party_name", "a".repeat(64), "TEXT")));

        assertThatThrownBy(() -> service.buildLineTable(12L, 9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("字段列名非法");

        verifyNoSchemaAccess();
    }

    private static Map<String, Object> lineTable() {
        return lineTable("am_archive_item_line_contract_party");
    }

    private static Map<String, Object> lineTable(String physicalTableName) {
        return Map.of(
                "id",
                12L,
                "categoryId",
                7L,
                "tableCode",
                "contract_party",
                "tableName",
                "合同方",
                "physicalTableName",
                physicalTableName,
                "sortOrder",
                1,
                "enabled",
                true);
    }

    private void verifyNoSchemaAccess() {
        verify(archiveMapper, never()).tableExists(anyString());
        verify(archiveMapper, never()).columnExists(anyString(), anyString());
        verify(archiveMapper, never()).executeSql(anyString());
    }

    private static Map<String, Object> lineField(
            Long id, String fieldCode, String columnName, String fieldType) {
        return Map.of(
                "id", id,
                "lineTableId", 12L,
                "fieldCode", fieldCode,
                "fieldName", fieldCode,
                "fieldType", fieldType,
                "columnName", columnName,
                "exactSearchable", false,
                "sortOrder", 1,
                "enabled", true);
    }
}
