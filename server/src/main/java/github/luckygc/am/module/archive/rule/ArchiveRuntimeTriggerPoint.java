package github.luckygc.am.module.archive.rule;

import github.luckygc.am.module.archive.ArchiveLevel;

public enum ArchiveRuntimeTriggerPoint {
    ITEM_BEFORE_CREATE(ArchiveLevel.ITEM, true),
    ITEM_BEFORE_UPDATE(ArchiveLevel.ITEM, true),
    ITEM_BEFORE_DELETE(ArchiveLevel.ITEM, false),
    VOLUME_BEFORE_CREATE(ArchiveLevel.VOLUME, true),
    VOLUME_BEFORE_ADD_ITEM(ArchiveLevel.VOLUME, false),
    FILE_BEFORE_UPLOAD(ArchiveLevel.ITEM, false),
    EXPORT_BEFORE_CREATE(ArchiveLevel.ITEM, false);

    private final ArchiveLevel archiveLevel;
    private final boolean fieldAssignmentAllowed;

    ArchiveRuntimeTriggerPoint(ArchiveLevel archiveLevel, boolean fieldAssignmentAllowed) {
        this.archiveLevel = archiveLevel;
        this.fieldAssignmentAllowed = fieldAssignmentAllowed;
    }

    public ArchiveLevel archiveLevel() {
        return archiveLevel;
    }

    public boolean fieldAssignmentAllowed() {
        return fieldAssignmentAllowed;
    }
}
