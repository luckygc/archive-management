package github.luckygc.am.module.archive.metadata;

import java.util.Locale;

public enum ArchiveFieldControl {
    input,
    textarea,
    number,
    date,
    datetime;

    public String value() {
        return name();
    }

    public static ArchiveFieldControl fromValue(String value) {
        return ArchiveFieldControl.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
