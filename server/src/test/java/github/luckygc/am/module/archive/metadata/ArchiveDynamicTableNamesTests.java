package github.luckygc.am.module.archive.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;

class ArchiveDynamicTableNamesTests {

    @Test
    void shouldUseSeparateDefaultTablesForMetadataAndPhysicalFields() {
        ArchiveCategoryDto category =
                new ArchiveCategoryDto(
                        7L,
                        1L,
                        null,
                        "contract",
                        "合同档案",
                        ArchiveManagementMode.VOLUME_ITEM,
                        null,
                        null,
                        null,
                        null,
                        ArchiveTableStatus.NOT_BUILT,
                        null,
                        true,
                        0,
                        null,
                        null);

        assertThat(
                        ArchiveDynamicTableNames.tableName(
                                category, ArchiveLevel.VOLUME, ArchiveFieldScope.METADATA))
                .isEqualTo("am_archive_volume_data_contract_800c327aefb3");
        assertThat(
                        ArchiveDynamicTableNames.tableName(
                                category, ArchiveLevel.VOLUME, ArchiveFieldScope.PHYSICAL))
                .isEqualTo("am_archive_volume_physical_contract_800c327aefb3");
        assertThat(
                        ArchiveDynamicTableNames.tableName(
                                category, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA))
                .isEqualTo("am_archive_item_data_contract_800c327aefb3");
        assertThat(
                        ArchiveDynamicTableNames.tableName(
                                category, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL))
                .isEqualTo("am_archive_item_physical_contract_800c327aefb3");
    }

    @Test
    void shouldKeepGeneratedNamesStableAndWithinPostgreSqlIdentifierLimit() {
        String stableKey = "Very-Long-Archive-Category-Code-For-Uat-And-Production-Import-2026";

        String first =
                ArchiveDynamicTableNames.stableIdentifier("am_archive_item_data_", stableKey);
        String second =
                ArchiveDynamicTableNames.stableIdentifier("am_archive_item_data_", stableKey);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSizeLessThanOrEqualTo(63);
        assertThat(first).startsWith("am_archive_item_data_very_long_archive_category");
        assertThat(first).endsWith("_66ff6755ef6d");
    }

    @Test
    void shouldAvoidCollisionsWhenReadableFragmentsAreEqual() {
        String first = ArchiveDynamicTableNames.stableIdentifier("am_archive_item_data_", "A-B");
        String second = ArchiveDynamicTableNames.stableIdentifier("am_archive_item_data_", "A_B");

        assertThat(first).startsWith("am_archive_item_data_a_b_");
        assertThat(second).startsWith("am_archive_item_data_a_b_");
        assertThat(first).isNotEqualTo(second);
    }
}
