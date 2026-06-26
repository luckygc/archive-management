package github.luckygc.am.module.archive.metadata;

import org.apache.commons.lang3.StringUtils;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveCategoryDto;

/** 动态档案表命名和层级规则。 */
public final class ArchiveDynamicTableNames {

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
        ArchiveLevel normalizedLevel = normalizeArchiveLevel(archiveLevel);
        String configuredName =
                normalizedLevel == ArchiveLevel.volume
                        ? category.volumeTableName()
                        : category.itemTableName();
        if (StringUtils.isNotBlank(configuredName)) {
            return configuredName;
        }
        return "am_archive_record_" + normalizedLevel.value().toLowerCase() + "_" + category.id();
    }
}
