package github.luckygc.am.module.archive.mapper;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;

public record ArchiveSqlCondition(
        String columnName,
        ArchiveItemQueryOperator operator,
        @Nullable Object value,
        @Nullable Object endValue) {

    public ArchiveSqlCondition(
            String columnName, ArchiveItemQueryOperator operator, @Nullable Object value) {
        this(columnName, operator, value, null);
    }

    public String operatorName() {
        return operator.name();
    }
}
