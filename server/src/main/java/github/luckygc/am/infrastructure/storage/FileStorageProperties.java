package github.luckygc.am.infrastructure.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@ConfigurationProperties(prefix = "archive.storage")
public class FileStorageProperties {

    private String adapter = "LOCAL";

    private String activeLocalBucket = "";

    private List<Local> local = new ArrayList<>();

    private ObjectStorage object = new ObjectStorage();

    @Setter
    @Getter
    public static class Local {

        private String bucket = "";

        private Path root;
    }

    @Setter
    @Getter
    public static class ObjectStorage {

        private String endpoint = "";

        private String region = "us-east-1";

        private String bucket = "";

        private String accessKey = "";

        private String secretKey = "";

        private boolean pathStyleAccess = true;
    }
}
