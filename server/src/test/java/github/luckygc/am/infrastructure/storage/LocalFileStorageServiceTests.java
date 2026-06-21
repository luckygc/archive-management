package github.luckygc.am.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageServiceTests {

    @TempDir Path tempDir;

    @Test
    void putGetExistsAndDeleteObject() throws Exception {
        FileStorageProperties.Local properties = new FileStorageProperties.Local();
        properties.setRoot(tempDir);
        LocalFileStorageService service = new LocalFileStorageService(properties);

        byte[] content = "archive file".getBytes(StandardCharsets.UTF_8);
        StorageObjectInfo stored =
                service.putObject(
                        "fonds/demo.txt",
                        new ByteArrayInputStream(content),
                        content.length,
                        "text/plain");

        assertThat(stored.objectKey()).isEqualTo("fonds/demo.txt");
        assertThat(stored.contentLength()).isEqualTo(content.length);
        assertThat(service.objectExists("fonds/demo.txt")).isTrue();

        try (FileStorageResource resource = service.getObject("fonds/demo.txt")) {
            assertThat(resource.objectKey()).isEqualTo("fonds/demo.txt");
            assertThat(resource.contentLength()).isEqualTo(content.length);
            assertThat(resource.inputStream().readAllBytes()).isEqualTo(content);
        }

        service.deleteObject("fonds/demo.txt");
        assertThat(service.objectExists("fonds/demo.txt")).isFalse();
    }

    @Test
    void rejectTraversalObjectKey() {
        assertThatThrownBy(() -> ObjectKeys.normalize("../demo.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
