package github.luckygc.am.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

import github.luckygc.am.common.storage.StorageType;

public record FileStorageResource(
        StorageType storageType,
        String bucketName,
        String objectKey,
        InputStream inputStream,
        long contentLength,
        String contentType)
        implements AutoCloseable {

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
