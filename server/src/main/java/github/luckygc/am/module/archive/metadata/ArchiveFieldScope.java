package github.luckygc.am.module.archive.metadata;

public enum ArchiveFieldScope {
    METADATA,
    PHYSICAL;

    public static ArchiveFieldScope fromValue(String value) {
        return ArchiveFieldScope.valueOf(value.trim());
    }

    public String value() {
        return name();
    }
}
