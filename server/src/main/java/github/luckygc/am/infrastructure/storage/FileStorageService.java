package github.luckygc.am.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 对象存储统一入口。
 */
public interface FileStorageService {

    StorageObjectInfo putObject(
            String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException;

    StorageObjectInfo putObject(
            StorageType storageType,
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType)
            throws IOException;

    FileStorageResource getObject(String objectKey) throws IOException;

    FileStorageResource getObject(StorageType storageType, String bucketName, String objectKey)
            throws IOException;

    boolean objectExists(String objectKey) throws IOException;

    boolean objectExists(StorageType storageType, String bucketName, String objectKey)
            throws IOException;

    void deleteObject(String objectKey) throws IOException;

    void deleteObject(StorageType storageType, String bucketName, String objectKey)
            throws IOException;

    StorageType defaultStorageType();

    String bucketName(StorageType storageType);
}
