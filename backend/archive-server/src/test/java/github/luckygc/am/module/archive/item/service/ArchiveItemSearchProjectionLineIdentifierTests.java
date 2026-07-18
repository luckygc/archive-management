package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;

@DisplayName("搜索投影明细标识符白名单")
class ArchiveItemSearchProjectionLineIdentifierTests {

    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ArchiveItemSearchProjectionService service =
            new ArchiveItemSearchProjectionService(
                    mock(ArchiveMetadataService.class),
                    mock(ArchiveCategoryService.class),
                    archiveMapper);

    @Test
    @DisplayName("非法明细物理表名不得进入 schema 探测或动态 SQL")
    void shouldRejectInvalidLineTableIdentifierBeforeMapperUse() {
        when(archiveMapper.listItemLineTables(7L)).thenReturn(List.of(lineTable("bad-table")));

        assertThatThrownBy(() -> service.upsert(3L, category(), List.of(), Map.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("动态明细表名非法");

        verify(archiveMapper, never()).tableExists("bad-table");
        verify(archiveMapper, never())
                .listItemLineRowsForProjection(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("超长明细列名不得进入动态投影 SQL")
    void shouldRejectOverlongLineColumnBeforeDynamicSql() {
        when(archiveMapper.listItemLineTables(7L))
                .thenReturn(List.of(lineTable("am_archive_item_line_contract")));
        when(archiveMapper.tableExists("am_archive_item_line_contract")).thenReturn(1);
        when(archiveMapper.listItemLineFields(4L)).thenReturn(List.of(lineField("a".repeat(64))));

        assertThatThrownBy(() -> service.upsert(3L, category(), List.of(), Map.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("字段列名非法");

        verify(archiveMapper, never())
                .listItemLineRowsForProjection(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("搜索投影跳过启用字段列尚未物化的明细表")
    void shouldSkipLineTableWithUnmaterializedColumn() {
        when(archiveMapper.listItemLineTables(7L))
                .thenReturn(List.of(lineTable("am_archive_item_line_contract")));
        when(archiveMapper.tableExists("am_archive_item_line_contract")).thenReturn(1);
        when(archiveMapper.listItemLineFields(4L)).thenReturn(List.of(lineField("f_party_name")));
        when(archiveMapper.columnExists("am_archive_item_line_contract", "f_party_name"))
                .thenReturn(0);

        service.upsert(3L, category(), List.of(), Map.of());

        verify(archiveMapper, never())
                .listItemLineRowsForProjection(org.mockito.ArgumentMatchers.any());
    }

    private static Map<String, Object> lineTable(String physicalTableName) {
        return Map.of("id", 4L, "physicalTableName", physicalTableName);
    }

    private static Map<String, Object> lineField(String columnName) {
        return Map.of("columnName", columnName, "fieldName", "字段");
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
}
