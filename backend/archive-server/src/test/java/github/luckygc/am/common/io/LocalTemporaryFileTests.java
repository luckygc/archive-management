package github.luckygc.am.common.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("本地临时文件")
class LocalTemporaryFileTests {

    @Test
    @DisplayName("关闭时删除临时文件并允许重复关闭")
    void closeShouldDeleteTemporaryFileAndBeIdempotent() throws Exception {
        Path path;
        try (LocalTemporaryFile temporaryFile = LocalTemporaryFile.create("am-export-", ".xlsx")) {
            path = temporaryFile.path();
            assertThat(path).startsWith(Path.of(System.getProperty("java.io.tmpdir")));
            assertThat(Files.exists(path)).isTrue();
            Files.writeString(path, "demo");
        }

        assertThat(Files.exists(path)).isFalse();

        LocalTemporaryFile temporaryFile = LocalTemporaryFile.create("am-export-", ".xlsx");
        Path anotherPath = temporaryFile.path();
        temporaryFile.close();
        temporaryFile.close();

        assertThat(Files.exists(anotherPath)).isFalse();
    }
}
