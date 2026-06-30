package github.luckygc.am.common.storage;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

public record StorageObjectInfo(
        StorageType storageType,
        String bucketName,
        String objectKey,
        long contentLength,
        @Nullable String contentType,
        Instant lastModified,
        @Nullable String eTag) {}
