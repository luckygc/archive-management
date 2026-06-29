package github.luckygc.am.module.archive.item;

public enum ArchiveItemQueryOperator {
    EQ,
    CONTAINS,
    STARTS_WITH,
    GTE,
    LTE,
    BETWEEN,
    IS_EMPTY,
    IS_NOT_EMPTY;

    public static ArchiveItemQueryOperator fromValue(String value) {
        return ArchiveItemQueryOperator.valueOf(value.trim().toUpperCase());
    }
}
