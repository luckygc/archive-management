package github.luckygc.am.module.archive.metadata;

import java.util.Locale;

public enum ArchiveManagementMode {
    item_only,
    volume_item;

    public String value() {
        return name();
    }

    public static ArchiveManagementMode fromValue(String value) {
        return ArchiveManagementMode.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
