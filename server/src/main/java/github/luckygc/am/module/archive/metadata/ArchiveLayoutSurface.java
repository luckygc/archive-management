package github.luckygc.am.module.archive.metadata;

public enum ArchiveLayoutSurface {
    TABLE,
    DETAIL,
    EDIT;

    public String value() {
        return name();
    }

    public static ArchiveLayoutSurface fromValue(String value) {
        return ArchiveLayoutSurface.valueOf(value.trim());
    }
}
