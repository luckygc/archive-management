package github.luckygc.am.module.authorization.service;

public enum AuthorizationPermissionCode {
    ARCHIVE_ITEM_READ("archive:item:read"),
    ARCHIVE_ITEM_CREATE("archive:item:create"),
    ARCHIVE_ITEM_UPDATE("archive:item:update"),
    ARCHIVE_ITEM_DELETE("archive:item:delete"),
    ARCHIVE_ITEM_LOCK("archive:item:lock"),
    ARCHIVE_ITEM_DOWNLOAD_ELECTRONIC_FILE("archive:item:download-electronic-file"),
    ARCHIVE_EXPORT("archive:export"),
    ARCHIVE_METADATA_MANAGE("archive:metadata:manage"),
    ARCHIVE_GOVERNANCE_MANAGE("archive:governance:manage"),
    AUTHORIZATION_PERMISSION_MANAGE("authorization:permission:manage"),
    AUTHENTICATION_SESSION_MANAGE("authentication:session:manage"),
    AUTHENTICATION_AUDIT_READ("authentication:audit:read"),
    ARCHIVE_DATA_SCOPE_MANAGE("archive:data-scope:manage"),
    AUTHENTICATION_USER_MANAGE("authentication:user:manage"),
    AUTHORIZATION_ROLE_MANAGE("authorization:role:manage"),
    ORGANIZATION_DEPARTMENT_MANAGE("organization:department:manage"),
    APPROVAL_DEFINITION_MANAGE("approval:definition:manage"),
    APPROVAL_INSTANCE_START("approval:instance:start"),
    APPROVAL_INSTANCE_MANAGE("approval:instance:manage");

    private final String code;

    AuthorizationPermissionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
