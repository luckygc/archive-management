package github.luckygc.am.common.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class RuntimeDirectoryInitializer {

    private static final String INIT_ARGUMENT = "--init";
    private static final List<Path> RUNTIME_DIRECTORIES = List.of(
            Path.of("data"),
            Path.of("data", "uploads"),
            Path.of("data", "files"),
            Path.of("logs")
    );

    private RuntimeDirectoryInitializer() {
    }

    public static boolean initializeIfRequested(String[] args) {
        if (Arrays.stream(args).noneMatch(INIT_ARGUMENT::equals)) {
            return false;
        }
        initialize();
        return true;
    }

    private static void initialize() {
        Path baseDir = Path.of("").toAbsolutePath().normalize();
        System.out.println("初始化运行目录: " + baseDir);
        for (Path directory : RUNTIME_DIRECTORIES) {
            Path target = baseDir.resolve(directory).normalize();
            createDirectory(target);
            System.out.println("已就绪: " + target);
        }
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("初始化运行目录失败: " + directory, ex);
        }
    }
}
