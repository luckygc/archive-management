package github.luckygc.am.module.archive.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;

/** 按已解析的字段定义转换字段编码和数据库列，不执行数据库操作。 */
final class ArchiveItemDynamicValues {

    private ArchiveItemDynamicValues() {}

    static Map<String, @Nullable Object> byFieldCode(
            List<ArchiveFieldDto> fields, Map<String, @Nullable Object> row) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        fields.forEach(field -> values.put(field.fieldCode(), row.get(field.columnName())));
        return values;
    }

    static Map<String, @Nullable Object> byColumnName(
            List<ArchiveFieldDto> fields, Map<String, @Nullable Object> values) {
        Map<String, @Nullable Object> row = new LinkedHashMap<>();
        fields.forEach(field -> row.put(field.columnName(), values.get(field.fieldCode())));
        return row;
    }

    static Map<String, @Nullable Object> merge(
            Map<String, @Nullable Object> current, Map<String, @Nullable Object> requested) {
        Map<String, @Nullable Object> merged = new LinkedHashMap<>(current);
        merged.putAll(requested);
        return merged;
    }

    static InsertValues insertValues(
            Long recordId,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> convertedFields) {
        StringBuilder columns = new StringBuilder("id");
        List<Object> values = new ArrayList<>();
        values.add(recordId);
        for (ArchiveFieldDto field : fields) {
            columns.append(", ").append(field.columnName());
            values.add(convertedFields.get(field.fieldCode()));
        }
        return new InsertValues(columns.toString(), values);
    }

    static List<ArchiveSqlAssignment> assignments(
            List<ArchiveFieldDto> fields, Map<String, @Nullable Object> convertedFields) {
        List<ArchiveSqlAssignment> assignments = new ArrayList<>();
        for (ArchiveFieldDto field : fields) {
            assignments.add(
                    new ArchiveSqlAssignment(
                            field.columnName(), convertedFields.get(field.fieldCode())));
        }
        return assignments;
    }

    record InsertValues(String columns, List<Object> values) {}
}
