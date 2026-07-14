package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("物理表已存在时仅补充后来新增的明细字段列")
    void buildExistingTableShouldAddOnlyMissingColumns() {
        when(archiveMapper.getItemLineTable(12L)).thenReturn(lineTable());
        when(archiveMapper.listItemLineFields(12L))
                .thenReturn(
                        List.of(
                                lineField(21L, "party_name", "f_party_name", "TEXT"),
                                lineField(22L, "amount", "f_amount", "DECIMAL")));
        when(archiveMapper.tableExists("am_archive_item_line_contract_party")).thenReturn(1);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_party_name"))
                .thenReturn(1);
        when(archiveMapper.columnExists("am_archive_item_line_contract_party", "f_amount"))
                .thenReturn(0);

        service.buildLineTable(12L, 9L);

        verify(archiveMapper)
                .executeSql(
                        "alter table am_archive_item_line_contract_party add column f_amount numeric");
        verify(archiveMapper, never())
                .executeSql(
                        "alter table am_archive_item_line_contract_party add column f_party_name text");
        verify(archiveMapper).columnExists("am_archive_item_line_contract_party", "f_party_name");
        verify(archiveMapper).columnExists("am_archive_item_line_contract_party", "f_amount");
    }

    @Test
    @DisplayName("物理表已存在时非法字段列名在执行 DDL 前失败")
    void buildExistingTableShouldRejectInvalidColumnName() {
        when(archiveMapper.getItemLineTable(12L)).thenReturn(lineTable());
        when(archiveMapper.listItemLineFields(12L))
                .thenReturn(List.of(lineField(21L, "party_name", "bad-column", "TEXT")));
        when(archiveMapper.tableExists("am_archive_item_line_contract_party")).thenReturn(1);

        assertThatThrownBy(() -> service.buildLineTable(12L, 9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("字段列名非法");

        verify(archiveMapper, never())
                .columnExists("am_archive_item_line_contract_party", "bad-column");
        verify(archiveMapper, never())
                .executeSql(
                        "alter table am_archive_item_line_contract_party add column bad-column text");
    }

    private static Map<String, Object> lineTable() {
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
                "am_archive_item_line_contract_party",
                "sortOrder",
                1,
                "enabled",
                true);
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
