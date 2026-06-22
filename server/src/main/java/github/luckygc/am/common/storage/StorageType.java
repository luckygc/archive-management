package github.luckygc.am.common.storage;

import java.util.Locale;

public enum StorageType {
    local,
    s3,
    minio,
    cos,
    oss,
    obs;

    public String value() {
        return name();
    }

    public static StorageType fromValue(String value) {
        return StorageType.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
