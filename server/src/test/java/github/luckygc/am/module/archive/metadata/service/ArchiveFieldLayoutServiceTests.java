package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldLayoutDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldLayoutItemRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldLayoutRequest;

@DisplayName("档案字段布局服务")
class ArchiveFieldLayoutServiceTests {

    private final ArchiveFieldLayoutDataRepository repository =
            mock(ArchiveFieldLayoutDataRepository.class);
    private final ArchiveFieldLayoutService service =
            new ArchiveFieldLayoutService(repository, new ArchiveFieldDefinitionService());

    @Test
    @DisplayName("无保存布局时生成默认表格布局")
    void publicLayoutItemsShouldReturnDefaultItems() {
        List<ArchiveMetadataService.ArchiveFieldLayoutItemDto> items =
                service.publicLayoutItems(1L, ArchiveLayoutSurface.TABLE, List.of(field(2L)));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().fieldId()).isEqualTo(2L);
        assertThat(items.getFirst().visible()).isTrue();
        assertThat(items.getFirst().rowOrder()).isEqualTo(0);
    }

    @Test
    @DisplayName("保存布局时拒绝重复字段")
    void savePublicLayoutShouldRejectDuplicateField() {
        ArchiveFieldDto field = field(2L);

        assertThatThrownBy(
                        () ->
                                service.savePublicLayout(
                                        1L,
                                        ArchiveLayoutSurface.TABLE,
                                        List.of(field),
                                        new ArchiveFieldLayoutRequest(
                                                List.of(
                                                        new ArchiveFieldLayoutItemRequest(
                                                                2L, true, 120, 1, 0, 0),
                                                        new ArchiveFieldLayoutItemRequest(
                                                                2L, true, 130, 1, 1, 0)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("布局字段不能重复");
    }

    private static ArchiveFieldDto field(Long id) {
        return new ArchiveFieldDto(
                id,
                1L,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                "title",
                "标题",
                ArchiveFieldType.TEXT,
                "f_title",
                100,
                null,
                null,
                ArchiveFieldControl.INPUT,
                true,
                160,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                false,
                false,
                true,
                0,
                null,
                LocalDateTime.now(),
                LocalDateTime.now());
    }
}
