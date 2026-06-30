package github.luckygc.am.module.authorization.service;

public enum AuthorizationPermissionCode {
    ARCHIVE_ITEM_READ("archive:item:read"),
    ARCHIVE_ITEM_CREATE("archive:item:create"),
    ARCHIVE_ITEM_UPDATE("archive:item:update"),
    ARCHIVE_ITEM_DELETE("archive:item:delete"),
    ARCHIVE_ITEM_LOCK("archive:item:lock"),
    ARCHIVE_FILE_BIND("archive:file:bind"),
    ARCHIVE_FILE_DOWNLOAD("archive:file:download"),
    ARCHIVE_AUDIT_READ("archive:audit:read"),
    ARCHIVE_EXPORT("archive:export"),
    ARCHIVE_METADATA_MANAGE("archive:metadata:manage"),
    AUTHORIZATION_PERMISSION_MANAGE("authorization:permission:manage"),
    ARCHIVE_DATA_SCOPE_MANAGE("archive:data-scope:manage");

    private final String code;

    AuthorizationPermissionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
