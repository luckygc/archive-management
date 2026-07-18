package github.luckygc.am.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.storage.FileStorageService;

@DisplayName("文件存储配置")
class FileStorageConfigurationTests {

    private final FileStorageConfiguration configuration = new FileStorageConfiguration();

    @Test
    @DisplayName("完整 S3 兼容配置创建唯一文件存储服务")
    void completeConfigurationCreatesFileStorageService() throws Exception {
        FileStorageProperties properties = objectStorageProperties();

        try (S3CompatibleFileStorageService service =
                (S3CompatibleFileStorageService) configuration.fileStorageService(properties)) {
            assertThat((FileStorageService) service).isNotNull();
        }
    }

    @Test
    @DisplayName("S3 兼容配置不完整时拒绝创建服务")
    void rejectIncompleteConfiguration() {
        FileStorageProperties properties = new FileStorageProperties();

        assertThatThrownBy(() -> configuration.fileStorageService(properties))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("archive.storage.bucket");
    }

    @Test
    @DisplayName("文件记录 bucket 与当前配置不一致时拒绝访问")
    void rejectStorageObjectFromDifferentBucket() throws Exception {
        FileStorageProperties properties = objectStorageProperties();

        try (S3CompatibleFileStorageService service =
                (S3CompatibleFileStorageService) configuration.fileStorageService(properties)) {
            assertThatThrownBy(() -> service.getObject("other", "2026/07/demo.pdf"))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("bucket 与当前配置不一致");
        }
    }

    private FileStorageProperties objectStorageProperties() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBucket("archive");
        properties.setRegion("us-east-1");
        properties.setAccessKey("access-key");
        properties.setSecretKey("secret-key");
        return properties;
    }
}
