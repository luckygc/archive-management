package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService.ArchiveItemOrderByRequest;

class ArchiveItemSortQueryTests {

    @Test
    void parsesOrderedFieldsAndDirections() {
        assertThat(ArchiveItemSortQuery.parse("+archiveYear,-createdAt"))
                .isEqualTo(
                        List.of(
                                new ArchiveItemOrderByRequest("archiveYear", "ASC"),
                                new ArchiveItemOrderByRequest("createdAt", "DESC")));
    }

    @Test
    void rejectsMalformedSortInsteadOfSilentlyChangingOrder() {
        for (String sort : List.of("", "createdAt", "+", "+id,", "+id, archiveNo")) {
            assertThatThrownBy(() -> ArchiveItemSortQuery.parse(sort))
                    .as("sort=%s", sort)
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
