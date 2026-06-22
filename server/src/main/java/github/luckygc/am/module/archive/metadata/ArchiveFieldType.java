package github.luckygc.am.module.archive.metadata;

import java.util.Locale;

public enum ArchiveFieldType {
    text,
    integer,
    decimal,
    date,
    datetime;

    public String value() {
        return name();
    }

    public static ArchiveFieldType fromValue(String value) {
        return ArchiveFieldType.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
