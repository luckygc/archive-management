package github.luckygc.am.module.archive.metadata;

public enum ArchiveManagementMode {
    ITEM_ONLY,
    VOLUME_ITEM;

    public String value() {
        return name();
    }

    public static ArchiveManagementMode fromValue(String value) {
        return ArchiveManagementMode.valueOf(value.trim());
    }
}
