package github.luckygc.am.infrastructure.storage;

import java.time.Instant;

import github.luckygc.am.common.storage.StorageType;

public record StorageObjectInfo(
        StorageType storageType,
        String bucketName,
        String objectKey,
        long contentLength,
        String contentType,
        Instant lastModified,
        String eTag) {}
