package github.luckygc.am.infrastructure.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "archive.storage")
public class FileStorageProperties {

    private String activeLocalBucket = "local";

    private List<Local> local = new ArrayList<>(List.of(new Local()));

    private ObjectStorage object = new ObjectStorage();

    @Setter
    @Getter
    public static class Local {

        private String bucket = "local";

        private Path root = Path.of("data/files");

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
