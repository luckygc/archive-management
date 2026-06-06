package github.luckygc.am.infrastructure.storage;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;

import com.github.f4b6a3.uuid.UuidCreator;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

public final class ObjectKeys {

    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9][a-z0-9._-]{0,31}");

    private ObjectKeys() {
    }

    public static String generate(String originalFilename) {
        return generate(LocalDate.now(ZoneId.systemDefault()), originalFilename);
    }

    static String generate(LocalDate date, String originalFilename) {
        String extension = extension(originalFilename);
        String objectKey = "%04d/%02d/%02d/%s%s".formatted(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                UuidCreator.getTimeOrderedEpoch(),
                extension
        );
        return normalize(objectKey);
    }

    public static String normalize(String objectKey) {
        if (StringUtils.isBlank(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        String normalized = FilenameUtils.separatorsToUnix(objectKey);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (StringUtils.isBlank(normalized) || normalized.contains("//") || normalized.contains("\0")) {
            throw new IllegalArgumentException("objectKey 不合法: " + objectKey);
        }
        try {
            Path path = Path.of(normalized).normalize();
            if (path.isAbsolute() || path.startsWith("..") || StringUtils.isBlank(path.toString())) {
                throw new IllegalArgumentException("objectKey 不允许越界: " + objectKey);
            }
            return FilenameUtils.separatorsToUnix(path.toString());
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("objectKey 不合法: " + objectKey, ex);
        }
    }

    private static String extension(String originalFilename) {
        String extension = StringUtils.trimToNull(FilenameUtils.getExtension(originalFilename));
        if (StringUtils.isBlank(extension)) {
            return "";
        }
        String normalizedExtension = StringUtils.lowerCase(extension, Locale.ROOT);
        return SAFE_EXTENSION.matcher(normalizedExtension).matches() ? "." + normalizedExtension : "";
    }

}
