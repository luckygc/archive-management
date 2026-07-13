package github.luckygc.am.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "archive.storage")
public class FileStorageProperties {

    private String endpoint = "";

    private String region = "us-east-1";

    private String bucket = "";

    private String accessKey = "";

    private String secretKey = "";

    private boolean pathStyleAccess = true;
}
