package github.luckygc.am.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import github.luckygc.am.common.storage.StorageType;

@DisplayName("委派文件存储服务")
class DelegatingFileStorageServiceTests {

    @TempDir Path tempDir;

    @Test
    @DisplayName("上传时使用默认存储类型和 bucket")
    void useDefaultStorageTypeWhenUpload() throws Exception {
        FileStorageBackend localBackend = localBackend();
        DelegatingFileStorageService service =
                new DelegatingFileStorageService(
                        StorageType.local, "local-test", List.of(localBackend));

        byte[] content = "archive file".getBytes(StandardCharsets.UTF_8);
        StorageObjectInfo stored =
                service.putObject(
                        "2026/06/06/demo.txt",
                        new ByteArrayInputStream(content),
                        content.length,
                        "text/plain");

        assertThat(stored.storageType()).isEqualTo(StorageType.local);
        assertThat(stored.bucketName()).isEqualTo("local-test");
        assertThat(service.defaultStorageType()).isEqualTo(StorageType.local);
    }

    @Test
    @DisplayName("未配置的存储位置会被拒绝")
    void rejectStorageTypeWithoutConfiguredBackend() {
        DelegatingFileStorageService service =
                new DelegatingFileStorageService(
                        StorageType.local, "local-test", List.of(localBackend()));

        assertThatThrownBy(
                        () -> service.objectExists(StorageType.s3, "bucket", "2026/06/06/demo.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("未启用文件存储位置: s3/bucket");
    }

    @Test
    @DisplayName("腾讯 COS 按对象存储 bucket 路由")
    void routeTencentCosByStorageTypeAndBucketName() {
        DelegatingFileStorageService service =
                new DelegatingFileStorageService(
                        StorageType.s3,
                        "archive",
                        List.of(
                                objectBackend(StorageType.s3, "archive"),
                                objectBackend(StorageType.minio, "archive"),
                                objectBackend(StorageType.cos, "archive"),
                                objectBackend(StorageType.oss, "archive"),
                                objectBackend(StorageType.obs, "archive")));

        assertThat(service.bucketName(StorageType.cos)).isEqualTo("archive");
    }

    @Test
    @DisplayName("本地存储可按 bucket 路由读取")
    void routeLocalStorageByBucketName() throws Exception {
        LocalFileStorageService dataBackend = localBackend("data", tempDir.resolve("data"));
        LocalFileStorageService nasBackend = localBackend("nas", tempDir.resolve("nas"));
        DelegatingFileStorageService service =
                new DelegatingFileStorageService(
                        StorageType.local, "data", List.of(dataBackend, nasBackend));

        byte[] content = "nas file".getBytes(StandardCharsets.UTF_8);
        dataBackend.putObject(
                "2026/06/06/demo.txt",
                new ByteArrayInputStream("data file".getBytes(StandardCharsets.UTF_8)),
                9,
                "text/plain");
        nasBackend.putObject(
                "2026/06/06/demo.txt",
                new ByteArrayInputStream(content),
                content.length,
                "text/plain");

        try (FileStorageResource resource =
                service.getObject(StorageType.local, "nas", "2026/06/06/demo.txt")) {
            assertThat(resource.bucketName()).isEqualTo("nas");
            assertThat(resource.inputStream().readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("上传写入 active local bucket")
    void uploadToActiveLocalBucket() throws Exception {
        LocalFileStorageService dataBackend = localBackend("data", tempDir.resolve("data"));
        LocalFileStorageService nasBackend = localBackend("nas", tempDir.resolve("nas"));
        DelegatingFileStorageService service =
                new DelegatingFileStorageService(
                        StorageType.local, "nas", List.of(dataBackend, nasBackend));

        byte[] content = "active nas file".getBytes(StandardCharsets.UTF_8);
        StorageObjectInfo stored =
                service.putObject(
                        "2026/06/06/active.txt",
                        new ByteArrayInputStream(content),
                        content.length,
                        "text/plain");

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
            public StorageObjectInfo putObject(
                    String objectKey,
                    java.io.InputStream inputStream,
                    long contentLength,
                    String contentType) {
                return new StorageObjectInfo(
                        storageType, bucketName, objectKey, contentLength, contentType, null, null);
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
            public void deleteObject(String objectKey) {}
        };
    }
}
