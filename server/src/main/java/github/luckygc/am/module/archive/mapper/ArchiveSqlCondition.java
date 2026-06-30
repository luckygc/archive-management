package github.luckygc.am.module.archive.mapper;

import java.util.List;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;

public record ArchiveSqlCondition(
        String columnName,
        ArchiveItemQueryOperator operator,
        @Nullable Object value,
        @Nullable Object endValue,
        List<?> values) {

    public ArchiveSqlCondition {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public ArchiveSqlCondition(
            String columnName,
            ArchiveItemQueryOperator operator,
            @Nullable Object value,
            @Nullable Object endValue) {
        this(columnName, operator, value, endValue, List.of());
    }

    public ArchiveSqlCondition(
            String columnName, ArchiveItemQueryOperator operator, @Nullable Object value) {
        this(columnName, operator, value, null, List.of());
    }

    public ArchiveSqlCondition(
            String columnName, ArchiveItemQueryOperator operator, List<?> values) {
        this(columnName, operator, null, null, values);
    }

    public String operatorName() {
        return operator.name();
    }
}
