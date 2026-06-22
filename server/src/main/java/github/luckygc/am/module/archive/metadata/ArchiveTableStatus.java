package github.luckygc.am.module.archive.metadata;

import java.util.Locale;

public enum ArchiveTableStatus {
    not_built,
    built;

    public String value() {
        return name();
    }

    public static ArchiveTableStatus fromValue(String value) {
        return ArchiveTableStatus.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
