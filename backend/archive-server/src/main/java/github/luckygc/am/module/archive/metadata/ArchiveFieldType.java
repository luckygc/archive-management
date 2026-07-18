package github.luckygc.am.module.archive.metadata;

public enum ArchiveFieldType {
    TEXT,
    INTEGER,
    DECIMAL,
    DATE,
    DATETIME;

    public String value() {
        return name();
    }

    public static ArchiveFieldType fromValue(String value) {
        return ArchiveFieldType.valueOf(value.trim());
    }
}
