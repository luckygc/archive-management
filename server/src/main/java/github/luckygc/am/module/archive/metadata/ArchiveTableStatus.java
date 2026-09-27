package github.luckygc.am.module.archive.metadata;

public enum ArchiveTableStatus {
    NOT_BUILT,
    BUILT;

    public String value() {
        return name();
    }

    public static ArchiveTableStatus fromValue(String value) {
        return ArchiveTableStatus.valueOf(value.trim());
    }
}
