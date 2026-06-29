package github.luckygc.am.module.archive.metadata;

public enum ArchiveFieldControl {
    INPUT,
    TEXTAREA,
    NUMBER,
    DATE,
    DATETIME;

    public String value() {
        return name();
    }

    public static ArchiveFieldControl fromValue(String value) {
        return ArchiveFieldControl.valueOf(value.trim());
    }
}
