package github.luckygc.am.module.archive.item;

import java.util.Locale;

public enum ArchiveItemSearchProjectionRebuildJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
