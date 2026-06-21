package github.luckygc.am.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DelegatingFileStorageService implements FileStorageService, AutoCloseable {

    private final StorageType defaultStorageType;

    private final String defaultBucketName;

    private final Map<BackendKey, FileStorageBackend> backends;

    DelegatingFileStorageService(
            StorageType defaultStorageType,
            String defaultBucketName,
            List<FileStorageBackend> backends) {
        this.defaultStorageType = defaultStorageType;
        this.defaultBucketName = defaultBucketName;
        this.backends = new HashMap<>();
        for (FileStorageBackend backend : backends) {
            BackendKey backendKey = new BackendKey(backend.storageType(), backend.bucketName());
            if (this.backends.put(backendKey, backend) != null) {
                throw new StorageException(
                        "文件存储位置重复配置: " + backend.storageType() + "/" + backend.bucketName());
            }
        }
        backend(defaultStorageType, defaultBucketName);
    }

    @Override
    public StorageObjectInfo putObject(
            String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException {
        return putObject(defaultStorageType, objectKey, inputStream, contentLength, contentType);
    }

    @Override
    public StorageObjectInfo putObject(
            StorageType storageType,
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType)
            throws IOException {
        return backend(storageType, bucketName(storageType))
                .putObject(objectKey, inputStream, contentLength, contentType);
    }

    @Override
    public FileStorageResource getObject(String objectKey) throws IOException {
        return getObject(defaultStorageType, defaultBucketName, objectKey);
    }

    @Override
    public FileStorageResource getObject(
            StorageType storageType, String bucketName, String objectKey) throws IOException {
        return backend(storageType, bucketName).getObject(objectKey);
    }

    @Override
    public boolean objectExists(String objectKey) throws IOException {
        return objectExists(defaultStorageType, defaultBucketName, objectKey);
    }

    @Override
    public boolean objectExists(StorageType storageType, String bucketName, String objectKey)
            throws IOException {
        return backend(storageType, bucketName).objectExists(objectKey);
    }

    @Override
    public void deleteObject(String objectKey) throws IOException {
        deleteObject(defaultStorageType, defaultBucketName, objectKey);
    }

    @Override
    public void deleteObject(StorageType storageType, String bucketName, String objectKey)
            throws IOException {
        backend(storageType, bucketName).deleteObject(objectKey);
    }

    @Override
    public StorageType defaultStorageType() {
        return defaultStorageType;
    }

    @Override
    public String bucketName(StorageType storageType) {
        if (storageType == defaultStorageType) {
            return defaultBucketName;
        }
        return backends.keySet().stream()
                .filter(key -> key.storageType() == storageType)
                .map(BackendKey::bucketName)
                .findFirst()
                .orElseThrow(() -> new StorageException("未启用文件存储类型: " + storageType));
    }

    private FileStorageBackend backend(StorageType storageType, String bucketName) {
        FileStorageBackend backend = backends.get(new BackendKey(storageType, bucketName));
        if (backend == null) {
            throw new StorageException("未启用文件存储位置: " + storageType + "/" + bucketName);
        }
        return backend;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (FileStorageBackend backend : backends.values()) {
            if (backend instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ex) {
                    if (failure == null) {
                        failure = new IOException("关闭文件存储后端失败", ex);
                    } else {
                        failure.addSuppressed(ex);
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private record BackendKey(StorageType storageType, String bucketName) {}
}
