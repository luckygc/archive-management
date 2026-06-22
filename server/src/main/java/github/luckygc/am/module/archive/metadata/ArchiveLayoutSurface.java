package github.luckygc.am.module.archive.metadata;

import java.util.Locale;

public enum ArchiveLayoutSurface {
    table,
    detail,
    edit;

    public String value() {
        return name();
    }

    public static ArchiveLayoutSurface fromValue(String value) {
        return ArchiveLayoutSurface.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
