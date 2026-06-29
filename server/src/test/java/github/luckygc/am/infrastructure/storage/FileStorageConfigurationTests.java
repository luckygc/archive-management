package github.luckygc.am.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import github.luckygc.am.common.storage.StorageType;

@DisplayName("文件存储配置")
class FileStorageConfigurationTests {

    @TempDir Path tempDir;

    private final FileStorageConfiguration configuration = new FileStorageConfiguration();

    @Test
    @DisplayName("默认 LOCAL adapter 使用 active local bucket")
    void defaultAdapterUsesActiveLocalBucket() throws Exception {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setActiveLocalBucket("local");
        FileStorageProperties.Local local = new FileStorageProperties.Local();
        local.setBucket("local");
        local.setRoot(tempDir);
        properties.getLocal().add(local);

        FileStorageService service = configuration.fileStorageService(properties);

        assertThat(service.defaultStorageType()).isEqualTo(StorageType.LOCAL);
        assertThat(service.bucketName(StorageType.LOCAL)).isEqualTo("local");
    }

    @Test
    @DisplayName("对象存储 adapter 使用配置的对象 bucket")
    void objectAdapterUsesConfiguredObjectBucket() throws Exception {
        FileStorageProperties properties = objectStorageProperties();
        properties.setAdapter("S3");

        try (DelegatingFileStorageService service =
                (DelegatingFileStorageService) configuration.fileStorageService(properties)) {
            assertThat(service.defaultStorageType()).isEqualTo(StorageType.S3);
            assertThat(service.bucketName(StorageType.S3)).isEqualTo("archive");
        }
    }

    @Test
    @DisplayName("对象存储配置不完整时拒绝创建服务")
    void rejectObjectAdapterWhenObjectStorageIsIncomplete() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setAdapter("S3");
        properties.setActiveLocalBucket("local");
        FileStorageProperties.Local local = new FileStorageProperties.Local();
        local.setBucket("local");
        local.setRoot(tempDir);
        properties.getLocal().add(local);

        assertThatThrownBy(() -> configuration.fileStorageService(properties))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("对象存储配置不完整");
    }

    @Test
    @DisplayName("不支持的文件存储 adapter 会被拒绝")
    void rejectUnsupportedStorageAdapter() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setAdapter("ftp");

        assertThatThrownBy(() -> configuration.fileStorageService(properties))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("文件存储 adapter 不支持: ftp");
    }

    private FileStorageProperties objectStorageProperties() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getObject().setBucket("archive");
        properties.getObject().setRegion("us-east-1");
        properties.getObject().setAccessKey("access-key");
        properties.getObject().setSecretKey("secret-key");
        return properties;
    }
}
