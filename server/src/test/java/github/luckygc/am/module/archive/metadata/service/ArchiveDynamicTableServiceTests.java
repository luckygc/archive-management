package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;

@DisplayName("档案动态表服务")
class ArchiveDynamicTableServiceTests {

    private final ArchiveMapper mapper = mock(ArchiveMapper.class);
    private final ArchiveDynamicTableService service =
            new ArchiveDynamicTableService(mapper, new ArchiveFieldDefinitionService());

    @Test
    @DisplayName("动态表不存在时创建 item 动态表并更新分类状态")
    void buildTableShouldCreateMissingItemTable() {
        ArchiveCategoryDto category = category();
        ArchiveFieldDto field = field("title", "f_title", ArchiveFieldType.TEXT, true);
        String tableName =
                service.dynamicTableName(category, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA);
        when(mapper.tableExists(tableName)).thenReturn(0);
        when(mapper.indexExists(anyString())).thenReturn(0);

        service.buildTable(
                category,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                List.of(field),
                List.of(),
                9L);

        verify(mapper).executeSql(contains("create table " + tableName));
        verify(mapper).executeSql(contains("f_title varchar(500)"));
        verify(mapper).executeSql(contains("create index"));
        verify(mapper)
                .updateCategoryTableStatus(
                        eq(1L), eq("ITEM"), eq("METADATA"), eq(tableName), eq("BUILT"), eq(9L));
    }

    @Test
    @DisplayName("字段编码变化时重命名动态表列")
    void syncDynamicColumnAfterFieldUpdateShouldRenameColumn() {
        ArchiveCategoryDto category = category();
        ArchiveFieldDto before = field("old_title", "f_old_title", ArchiveFieldType.TEXT, false);
        ArchiveFieldDto after = field("new_title", "f_new_title", ArchiveFieldType.TEXT, true);
        String tableName =
                service.dynamicTableName(category, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA);
        when(mapper.tableExists(tableName)).thenReturn(1);
        when(mapper.columnExists(tableName, "f_old_title")).thenReturn(1);
        when(mapper.columnExists(tableName, "f_new_title")).thenReturn(0);
        when(mapper.indexExists(anyString())).thenReturn(0);

        service.syncDynamicColumnAfterFieldUpdate(category, before, after);

        verify(mapper)
                .executeSql(
                        "alter table " + tableName + " rename column f_old_title to f_new_title");
        verify(mapper)
                .executeSql(
                        "alter table " + tableName + " alter column f_new_title type varchar(500)");
        verify(mapper).executeSql(contains("create index"));
    }

    @Test
    @DisplayName("唯一规则索引名使用稳定前缀")
    void uniqueConstraintIndexNameShouldUseStablePrefix() {
        assertThat(service.uniqueConstraintIndexName("contract", ArchiveLevel.ITEM, "archive_no"))
                .startsWith("uk_am_archive_constraint_");
    }

    private static ArchiveCategoryDto category() {
        return new ArchiveCategoryDto(
                1L,
                2L,
                null,
                "contract",
                "合同档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                null,
                null,
                null,
                ArchiveTableStatus.NOT_BUILT,
                null,
                true,
                0,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private static ArchiveFieldDto field(
            String fieldCode,
            String columnName,
            ArchiveFieldType fieldType,
            boolean exactSearchable) {
        return new ArchiveFieldDto(
                2L,
                1L,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                fieldCode,
                "标题",
                fieldType,
                columnName,
                null,
                null,
                null,
                ArchiveFieldControl.INPUT,
                true,
                null,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                exactSearchable,
                false,
                true,
                0,
                null,
                null,
                null);
    }
}
