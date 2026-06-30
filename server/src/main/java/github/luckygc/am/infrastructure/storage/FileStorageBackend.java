package github.luckygc.am.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.common.storage.StorageObjectInfo;
import github.luckygc.am.common.storage.StorageType;

interface FileStorageBackend {

    StorageType storageType();

    String bucketName();

    StorageObjectInfo putObject(
            String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException;

    FileStorageResource getObject(String objectKey) throws IOException;

    boolean objectExists(String objectKey) throws IOException;

    void deleteObject(String objectKey) throws IOException;
}
