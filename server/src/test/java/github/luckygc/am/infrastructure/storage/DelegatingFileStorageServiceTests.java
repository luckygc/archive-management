package github.luckygc.am.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DelegatingFileStorageServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void useDefaultStorageTypeWhenUpload() throws Exception {
        FileStorageBackend localBackend = localBackend();
        DelegatingFileStorageService service = new DelegatingFileStorageService(
                StorageType.LOCAL,
                "local-test",
                List.of(localBackend)
        );

        byte[] content = "archive file".getBytes(StandardCharsets.UTF_8);
        StorageObjectInfo stored = service.putObject(
                "2026/06/06/demo.txt",
                new ByteArrayInputStream(content),
                content.length,
                "text/plain"
        );

        assertThat(stored.storageType()).isEqualTo(StorageType.LOCAL);
        assertThat(stored.bucketName()).isEqualTo("local-test");
        assertThat(service.defaultStorageType()).isEqualTo(StorageType.LOCAL);
    }

    @Test
    void rejectStorageTypeWithoutConfiguredBackend() {
        DelegatingFileStorageService service = new DelegatingFileStorageService(
                StorageType.LOCAL,
                "local-test",
                List.of(localBackend())
        );

        assertThatThrownBy(() -> service.objectExists(StorageType.S3, "bucket", "2026/06/06/demo.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("未启用文件存储位置: S3/bucket");
    }

    @Test
    void routeTencentCosByStorageTypeAndBucketName() {
        DelegatingFileStorageService service = new DelegatingFileStorageService(
                StorageType.S3,
                "archive",
                List.of(
                        objectBackend(StorageType.S3, "archive"),
                        objectBackend(StorageType.MINIO, "archive"),
                        objectBackend(StorageType.COS, "archive"),
                        objectBackend(StorageType.OSS, "archive"),
                        objectBackend(StorageType.OBS, "archive")
                )
        );

        assertThat(service.bucketName(StorageType.COS)).isEqualTo("archive");
    }

    @Test
    void routeLocalStorageByBucketName() throws Exception {
        LocalFileStorageService dataBackend = localBackend("data", tempDir.resolve("data"));
        LocalFileStorageService nasBackend = localBackend("nas", tempDir.resolve("nas"));
        DelegatingFileStorageService service = new DelegatingFileStorageService(
                StorageType.LOCAL,
                "data",
                List.of(dataBackend, nasBackend)
        );

        byte[] content = "nas file".getBytes(StandardCharsets.UTF_8);
        dataBackend.putObject("2026/06/06/demo.txt", new ByteArrayInputStream("data file".getBytes(StandardCharsets.UTF_8)),
                9, "text/plain");
        nasBackend.putObject("2026/06/06/demo.txt", new ByteArrayInputStream(content), content.length, "text/plain");

        try (FileStorageResource resource = service.getObject(StorageType.LOCAL, "nas", "2026/06/06/demo.txt")) {
            assertThat(resource.bucketName()).isEqualTo("nas");
            assertThat(resource.inputStream().readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void uploadToActiveLocalBucket() throws Exception {
        LocalFileStorageService dataBackend = localBackend("data", tempDir.resolve("data"));
        LocalFileStorageService nasBackend = localBackend("nas", tempDir.resolve("nas"));
        DelegatingFileStorageService service = new DelegatingFileStorageService(
                StorageType.LOCAL,
                "nas",
                List.of(dataBackend, nasBackend)
        );

        byte[] content = "active nas file".getBytes(StandardCharsets.UTF_8);
        StorageObjectInfo stored = service.putObject(
                "2026/06/06/active.txt",
                new ByteArrayInputStream(content),
                content.length,
                "text/plain"
        );

        assertThat(stored.bucketName()).isEqualTo("nas");
        assertThat(dataBackend.objectExists("2026/06/06/active.txt")).isFalse();
        assertThat(nasBackend.objectExists("2026/06/06/active.txt")).isTrue();
    }

    private LocalFileStorageService localBackend() {
        return localBackend("local-test", tempDir);
    }

    private LocalFileStorageService localBackend(String bucket, Path root) {
        FileStorageProperties.Local properties = new FileStorageProperties.Local();
        properties.setBucket(bucket);
        properties.setRoot(root);
        return new LocalFileStorageService(properties);
    }

    private FileStorageBackend objectBackend(StorageType storageType, String bucketName) {
        return new FileStorageBackend() {
            @Override
            public StorageType storageType() {
                return storageType;
            }

            @Override
            public String bucketName() {
                return bucketName;
            }

            @Override
            public StorageObjectInfo putObject(String objectKey, java.io.InputStream inputStream, long contentLength,
                                               String contentType) {
                return new StorageObjectInfo(storageType, bucketName, objectKey, contentLength, contentType, null, null);
            }

            @Override
            public FileStorageResource getObject(String objectKey) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean objectExists(String objectKey) {
                return true;
            }

            @Override
            public void deleteObject(String objectKey) {
            }
        };
    }
}
