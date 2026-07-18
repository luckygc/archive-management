package github.luckygc.am.common.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 对象存储统一入口。
 */
public interface FileStorageService {

    StorageObjectInfo putObject(
            String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException;

    FileStorageResource getObject(String bucketName, String objectKey) throws IOException;

    boolean objectExists(String bucketName, String objectKey) throws IOException;

    void deleteObject(String bucketName, String objectKey) throws IOException;
}
