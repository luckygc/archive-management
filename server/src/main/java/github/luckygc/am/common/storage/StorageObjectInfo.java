package github.luckygc.am.common.storage;

import org.jspecify.annotations.Nullable;

public record StorageObjectInfo(
        String bucketName,
        String objectKey,
        long contentLength,
        @Nullable String contentType,
        @Nullable String eTag) {}
