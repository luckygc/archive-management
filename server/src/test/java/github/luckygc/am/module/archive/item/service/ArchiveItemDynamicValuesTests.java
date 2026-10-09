package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;

@DisplayName("档案条目动态字段持久化值映射")
class ArchiveItemDynamicValuesTests {

    @Test
    @DisplayName("列顺序与参数顺序一致，缺省空值保留且只写声明字段")
    void persistenceValuesFollowDeclaredColumnOrder() {
        List<ArchiveFieldDto> fields =
                List.of(field("title", "f_title"), field("count", "f_count"));
        Map<String, @Nullable Object> values = Map.of("title", "标题", "undeclared", "忽略");

        var insert = ArchiveItemDynamicValues.insertValues(7L, fields, values);

        assertThat(insert.columns()).isEqualTo("id, f_title, f_count");
        assertThat(insert.values()).containsExactly(7L, "标题", null);
        assertThat(ArchiveItemDynamicValues.assignments(fields, values))
                .containsExactly(
                        new ArchiveSqlAssignment("f_title", "标题"),
                        new ArchiveSqlAssignment("f_count", null));
        assertThat(ArchiveItemDynamicValues.byColumnName(fields, values))
                .containsEntry("f_title", "标题")
                .containsEntry("f_count", null)
                .doesNotContainKey("undeclared");
    }

    @Test
    @DisplayName("字段编码合并覆盖显式空值，原行及候选请求保持不变")
    void mergePreservesNullDeletionAndLeavesInputsUnchanged() {
        List<ArchiveFieldDto> fields =
                List.of(field("title", "f_title"), field("count", "f_count"));
        Map<String, @Nullable Object> row = Map.of("f_title", "原题名", "f_count", 5);
        Map<String, @Nullable Object> current = ArchiveItemDynamicValues.byFieldCode(fields, row);
        Map<String, @Nullable Object> requested = new LinkedHashMap<>();
        requested.put("title", null);

        var merged = ArchiveItemDynamicValues.merge(current, requested);

        assertThat(merged).containsEntry("title", null).containsEntry("count", 5);
        assertThat(current).containsExactlyInAnyOrderEntriesOf(Map.of("title", "原题名", "count", 5));
        assertThat(row).containsExactlyInAnyOrderEntriesOf(Map.of("f_title", "原题名", "f_count", 5));
        assertThat(requested).hasSize(1).containsEntry("title", null);
    }

    private ArchiveFieldDto field(String code, String column) {
        ArchiveFieldDto field = mock(ArchiveFieldDto.class);
        when(field.fieldCode()).thenReturn(code);
        when(field.columnName()).thenReturn(column);
        return field;
    }
}
