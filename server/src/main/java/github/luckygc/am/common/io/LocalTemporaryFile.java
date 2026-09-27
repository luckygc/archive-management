package github.luckygc.am.common.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalTemporaryFile implements AutoCloseable {

    private final Path path;
    private boolean closed;

    private LocalTemporaryFile(Path path) {
        this.path = path;
    }

    public static LocalTemporaryFile create(String prefix, String suffix) throws IOException {
        return new LocalTemporaryFile(Files.createTempFile(prefix, suffix));
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        Files.deleteIfExists(path);
        closed = true;
    }
}
