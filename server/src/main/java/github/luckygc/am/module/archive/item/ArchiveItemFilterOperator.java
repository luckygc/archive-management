package github.luckygc.am.module.archive.item;

public enum ArchiveItemFilterOperator {
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

    public static ArchiveItemFilterOperator fromValue(String value) {
        return ArchiveItemFilterOperator.valueOf(value.trim().toUpperCase());
    }
}
