package github.luckygc.am.module.archive;

import java.util.List;
import java.util.Locale;

public enum ArchiveLevel {
    volume,
    item;

    private static final List<ArchiveLevel> ORDERED_VALUES = List.of(values());

    public String value() {
        return name();
    }

    public static List<ArchiveLevel> orderedValues() {
        return ORDERED_VALUES;
    }

    public static ArchiveLevel fromValue(String value) {
        return ArchiveLevel.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
