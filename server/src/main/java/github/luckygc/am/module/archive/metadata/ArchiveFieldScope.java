package github.luckygc.am.module.archive.metadata;

import java.util.Locale;

public enum ArchiveFieldScope {
    metadata,
    physical;

    public static ArchiveFieldScope fromValue(String value) {
        return ArchiveFieldScope.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }

    public String value() {
        return name();
    }
}
