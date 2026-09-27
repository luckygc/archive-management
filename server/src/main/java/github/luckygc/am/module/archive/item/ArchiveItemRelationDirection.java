package github.luckygc.am.module.archive.item;

public enum ArchiveItemRelationDirection {
    OUTGOING,
    INCOMING,
    BOTH;

    public static ArchiveItemRelationDirection fromValue(String value) {
        return ArchiveItemRelationDirection.valueOf(value.trim().toUpperCase());
    }
}
