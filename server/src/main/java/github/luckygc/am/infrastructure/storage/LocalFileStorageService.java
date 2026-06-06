package github.luckygc.am.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

class LocalFileStorageService implements FileStorageBackend {

    private final String bucket;

    private final Path root;

    LocalFileStorageService(FileStorageProperties.Local properties) {
        this.bucket = StringUtils.defaultIfBlank(properties.getBucket(), "local").trim();
        this.root = properties.getRoot().toAbsolutePath().normalize();
    }

    @Override
    public StorageType storageType() {
        return StorageType.LOCAL;
    }

    @Override
    public String bucketName() {
        return bucket;
    }

    @Override
    public StorageObjectInfo putObject(String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException {
        String normalizedObjectKey = ObjectKeys.normalize(objectKey);
        Path target = resolve(normalizedObjectKey);
        Files.createDirectories(target.getParent());
        Path tempFile = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            long written = Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new StorageObjectInfo(storageType(), bucketName(), normalizedObjectKey, written, contentType,
                    Files.getLastModifiedTime(target).toInstant(), null);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public FileStorageResource getObject(String objectKey) throws IOException {
        String normalizedObjectKey = ObjectKeys.normalize(objectKey);
        Path file = resolve(normalizedObjectKey);
        if (!Files.exists(file)) {
            throw new NoSuchFileException(normalizedObjectKey);
        }
        String contentType = Files.probeContentType(file);
        return new FileStorageResource(storageType(), bucketName(), normalizedObjectKey, Files.newInputStream(file),
                Files.size(file), contentType);
    }

    @Override
    public boolean objectExists(String objectKey) {
        return Files.exists(resolve(ObjectKeys.normalize(objectKey)));
    }

    @Override
    public void deleteObject(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(ObjectKeys.normalize(objectKey)));
    }

    private Path resolve(String objectKey) {
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("objectKey 不允许越界: " + objectKey);
        }
        return path;
    }

}
