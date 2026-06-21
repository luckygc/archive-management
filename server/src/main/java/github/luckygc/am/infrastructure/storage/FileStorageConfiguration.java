package github.luckygc.am.infrastructure.storage;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FileStorageConfiguration {

    @Bean
    @ConditionalOnMissingBean
    FileStorageService fileStorageService(FileStorageProperties properties) {
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
        StorageType defaultStorageType =
                objectStorageConfigured ? StorageType.S3 : StorageType.LOCAL;
        String defaultBucketName =
                objectStorageConfigured
                        ? properties.getObject().getBucket().trim()
                        : requireText(
                                properties.getActiveLocalBucket(),
                                "archive.storage.active-local-bucket");
        return new DelegatingFileStorageService(defaultStorageType, defaultBucketName, backends);
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
