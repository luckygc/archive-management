package github.luckygc.am.module.archive.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder.Direction;

class ArchiveEnumContractTests {

    @Test
    void shouldUseUppercaseReadableValuesForApiAndDatabase() {
        assertThat(ArchiveLevel.ITEM.value()).isEqualTo("ITEM");
        assertThat(ArchiveFieldScope.METADATA.value()).isEqualTo("METADATA");
        assertThat(ArchiveFieldType.DATETIME.value()).isEqualTo("DATETIME");
        assertThat(ArchiveFieldControl.TEXTAREA.value()).isEqualTo("TEXTAREA");
        assertThat(ArchiveManagementMode.VOLUME_ITEM.value()).isEqualTo("VOLUME_ITEM");
        assertThat(ArchiveLayoutSurface.DETAIL.value()).isEqualTo("DETAIL");
        assertThat(ArchiveTableStatus.NOT_BUILT.value()).isEqualTo("NOT_BUILT");
    }

    @Test
    void shouldRejectLowercaseEnumValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> ArchiveLevel.fromValue("item"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ArchiveManagementMode.fromValue("volume_item"));
    }

    @Test
    void shouldKeepSqlOrderKeywordLowercase() {
        assertThat(new ArchiveSqlOrder("r.id", Direction.ASC).sql()).isEqualTo("r.id asc");
    }
}
