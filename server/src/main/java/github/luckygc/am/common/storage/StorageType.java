package github.luckygc.am.common.storage;

public enum StorageType {
    LOCAL,
    S3,
    MINIO,
    COS,
    OSS,
    OBS;

    public String value() {
        return name();
    }

    public static StorageType fromValue(String value) {
        return StorageType.valueOf(value.trim());
    }
}
