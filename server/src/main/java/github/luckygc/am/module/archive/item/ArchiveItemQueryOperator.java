package github.luckygc.am.module.archive.item;

public enum ArchiveItemQueryOperator {
    EQ,
    CONTAINS,
    STARTS_WITH,
    GTE,
    LTE,
    IN,
    BETWEEN,
    IS_NULL,
    IS_NOT_NULL,
    IS_EMPTY,
    IS_NOT_EMPTY;

    public static ArchiveItemQueryOperator fromValue(String value) {
        return ArchiveItemQueryOperator.valueOf(value.trim().toUpperCase());
    }
}
