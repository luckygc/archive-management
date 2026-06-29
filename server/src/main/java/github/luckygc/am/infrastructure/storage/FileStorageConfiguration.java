package github.luckygc.am.infrastructure.storage;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import github.luckygc.am.common.storage.StorageType;

@Configuration
class FileStorageConfiguration {

    @Bean
    @ConditionalOnMissingBean
    FileStorageService fileStorageService(FileStorageProperties properties) {
        StorageType defaultStorageType = defaultStorageType(properties);
        List<FileStorageBackend> backends = new ArrayList<>();
        for (FileStorageProperties.Local local : properties.getLocal()) {
            if (isLocalStorageConfigured(local)) {
                backends.add(new LocalFileStorageService(local));
            }
        }
        boolean objectStorageConfigured = isObjectStorageConfigured(properties.getObject());
        if (objectStorageConfigured) {
            backends.add(
                    new S3CompatibleFileStorageService(StorageType.S3, properties.getObject()));
            backends.add(
                    new S3CompatibleFileStorageService(StorageType.MINIO, properties.getObject()));
            backends.add(
                    new S3CompatibleFileStorageService(StorageType.COS, properties.getObject()));
            backends.add(
                    new S3CompatibleFileStorageService(StorageType.OSS, properties.getObject()));
            backends.add(
                    new S3CompatibleFileStorageService(StorageType.OBS, properties.getObject()));
        }
        if (backends.isEmpty()) {
            throw new StorageException("至少需要配置一种文件存储后端");
        }
        if (defaultStorageType != StorageType.LOCAL && !objectStorageConfigured) {
            throw new StorageException(
                    "已选择文件存储 adapter "
                            + defaultStorageType
                            + "，但对象存储配置不完整；请补齐 archive.storage.object 配置");
        }
        String defaultBucketName =
                defaultStorageType == StorageType.LOCAL
                        ? requireText(
                                properties.getActiveLocalBucket(),
                                "archive.storage.active-local-bucket")
                        : properties.getObject().getBucket().trim();
        return new DelegatingFileStorageService(defaultStorageType, defaultBucketName, backends);
    }

    private static StorageType defaultStorageType(FileStorageProperties properties) {
        try {
            return StorageType.fromValue(
                    requireText(properties.getAdapter(), "archive.storage.adapter"));
        } catch (IllegalArgumentException exception) {
            throw new StorageException(
                    "文件存储 adapter 不支持: "
                            + properties.getAdapter()
                            + "，支持 LOCAL、S3、MINIO、COS、OSS、OBS");
        }
    }

    private static boolean isLocalStorageConfigured(FileStorageProperties.Local properties) {
        return properties.getRoot() != null && StringUtils.isNotBlank(properties.getBucket());
    }

    private static boolean isObjectStorageConfigured(
            FileStorageProperties.ObjectStorage properties) {
        return StringUtils.isNoneBlank(
                properties.getBucket(),
                properties.getRegion(),
                properties.getAccessKey(),
                properties.getSecretKey());
    }

    private static String requireText(String value, String propertyName) {
        if (StringUtils.isBlank(value)) {
            throw new StorageException("缺少文件存储配置: " + propertyName);
        }
        return value.trim();
    }
}
