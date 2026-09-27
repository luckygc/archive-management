package github.luckygc.am.infrastructure.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import github.luckygc.am.common.storage.FileStorageService;

@Configuration
class FileStorageConfiguration {

    @Bean
    @ConditionalOnMissingBean
    FileStorageService fileStorageService(FileStorageProperties properties) {
        return new S3CompatibleFileStorageService(properties);
    }
}
