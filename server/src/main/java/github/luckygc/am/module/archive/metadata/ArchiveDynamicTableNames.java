package github.luckygc.am.module.archive.metadata;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveCategoryDto;

/** 动态档案表命名和层级规则。 */
public final class ArchiveDynamicTableNames {

    private static final int POSTGRESQL_IDENTIFIER_LIMIT = 63;
    private static final int HASH_SUFFIX_LENGTH = 12;
    private static final Pattern NON_IDENTIFIER_CHARS = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern REPEATED_UNDERSCORES = Pattern.compile("_+");

    private ArchiveDynamicTableNames() {}

    public static ArchiveLevel normalizeArchiveLevel(ArchiveLevel archiveLevel) {
        return archiveLevel == null ? ArchiveLevel.item : archiveLevel;
    }

    public static boolean isVolumeLevelAllowed(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        ArchiveLevel normalizedLevel = normalizeArchiveLevel(archiveLevel);
        return normalizedLevel != ArchiveLevel.volume
                || category.managementMode() == ArchiveManagementMode.volume_item;
    }

    public static String tableName(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return tableName(category, archiveLevel, ArchiveFieldScope.metadata);
    }

    public static String tableName(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        ArchiveLevel normalizedLevel = normalizeArchiveLevel(archiveLevel);
        ArchiveFieldScope normalizedScope = normalizeFieldScope(fieldScope);
        String configuredName =
                switch (normalizedScope) {
                    case metadata ->
                            normalizedLevel == ArchiveLevel.volume
                                    ? category.volumeTableName()
                                    : category.itemTableName();
                    case physical ->
                            normalizedLevel == ArchiveLevel.volume
                                    ? category.volumePhysicalTableName()
                                    : category.itemPhysicalTableName();
                };
        if (StringUtils.isNotBlank(configuredName)) {
            return configuredName;
        }
        String suffix =
                normalizedScope == ArchiveFieldScope.metadata
                        ? ""
                        : "_" + normalizedScope.value().toLowerCase();
        return stableIdentifier(
                "am_archive_record_" + normalizedLevel.value().toLowerCase() + suffix + "_",
                category.categoryCode());
    }

    public static ArchiveFieldScope normalizeFieldScope(ArchiveFieldScope fieldScope) {
        return fieldScope == null ? ArchiveFieldScope.metadata : fieldScope;
    }

    public static String stableIdentifier(String prefix, String stableKey) {
        String normalizedPrefix = StringUtils.trimToEmpty(prefix).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(normalizedPrefix) || !normalizedPrefix.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("数据库对象名前缀非法：" + prefix);
        }
        String readable = readableFragment(stableKey);
        String hash =
                DigestUtils.md5Hex(StringUtils.trimToEmpty(stableKey))
                        .substring(0, HASH_SUFFIX_LENGTH);
        int readableLimit =
                POSTGRESQL_IDENTIFIER_LIMIT - normalizedPrefix.length() - 1 - HASH_SUFFIX_LENGTH;
        if (readableLimit <= 0) {
            throw new IllegalArgumentException("数据库对象名前缀过长：" + prefix);
        }
        String truncatedReadable =
                StringUtils.stripEnd(
                        readable.substring(0, Math.min(readable.length(), readableLimit)), "_");
        if (StringUtils.isBlank(truncatedReadable)) {
            truncatedReadable = "key";
        }
        return normalizedPrefix + truncatedReadable + "_" + hash;
    }

    private static String readableFragment(String stableKey) {
        String readable =
                NON_IDENTIFIER_CHARS
                        .matcher(StringUtils.trimToEmpty(stableKey).toLowerCase(Locale.ROOT))
                        .replaceAll("_");
        readable = REPEATED_UNDERSCORES.matcher(readable).replaceAll("_");
        readable = StringUtils.strip(readable, "_");
        if (StringUtils.isBlank(readable)) {
            return "key";
        }
        if (!Character.isLetter(readable.charAt(0))) {
            return "k_" + readable;
        }
        return readable;
    }
}
