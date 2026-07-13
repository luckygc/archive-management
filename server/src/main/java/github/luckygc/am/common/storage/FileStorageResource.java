package github.luckygc.am.common.storage;

import java.io.IOException;
import java.io.InputStream;

import org.jspecify.annotations.Nullable;

public record FileStorageResource(
        String bucketName,
        String objectKey,
        InputStream inputStream,
        long contentLength,
        @Nullable String contentType)
        implements AutoCloseable {

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
