package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintRequest;

@DisplayName("档案唯一规则服务")
class ArchiveUniqueConstraintServiceTests {

    private final ArchiveMapper mapper = mock(ArchiveMapper.class);
    private final ArchiveFieldDefinitionService fieldDefinitionService =
            new ArchiveFieldDefinitionService();
    private final ArchiveDynamicTableService dynamicTableService =
            new ArchiveDynamicTableService(mapper, fieldDefinitionService);
    private final ArchiveUniqueConstraintService service =
            new ArchiveUniqueConstraintService(mapper, fieldDefinitionService, dynamicTableService);

    @Test
    @DisplayName("唯一规则字段不能为空")
    void validateShouldRejectEmptyFields() {
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        category(),
                                        List.of(
                                                field(
                                                        1L,
                                                        ArchiveLevel.ITEM,
                                                        ArchiveFieldScope.METADATA)),
                                        new ArchiveUniqueConstraintRequest(
                                                ArchiveLevel.ITEM,
                                                "archive_no_unique",
                                                "档号唯一",
                                                true,
                                                List.of())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("唯一约束字段不能为空");
    }

    @Test
    @DisplayName("唯一规则字段必须属于同一层级和元数据范围")
    void validateShouldRejectWrongFieldLevelOrScope() {
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        category(),
                                        List.of(
                                                field(
                                                        1L,
                                                        ArchiveLevel.VOLUME,
                                                        ArchiveFieldScope.METADATA)),
                                        new ArchiveUniqueConstraintRequest(
                                                ArchiveLevel.ITEM,
                                                "archive_no_unique",
                                                "档号唯一",
                                                true,
                                                List.of(1L))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("唯一约束字段必须和约束层级一致");
    }

    @Test
    @DisplayName("标记唯一规则字段为可精确检索")
    void markFieldsSearchableShouldDelegateToMapper() {
        service.markUniqueConstraintFieldsSearchable(
                category(),
                List.of(field(1L, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA)),
                List.of(1L),
                9L);

        verify(mapper).markFieldsExactSearchable(eq(1L), anyList());
    }

    private static ArchiveCategoryDto category() {
        return new ArchiveCategoryDto(
                1L,
                2L,
                null,
                "contract",
                "合同档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                null,
                null,
                null,
                ArchiveTableStatus.NOT_BUILT,
                null,
                true,
                0,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private static ArchiveFieldDto field(Long id, ArchiveLevel level, ArchiveFieldScope scope) {
        return new ArchiveFieldDto(
                id,
                1L,
                level,
                scope,
                "archive_title",
                "题名",
                ArchiveFieldType.TEXT,
                "f_archive_title",
                100,
                null,
                null,
                ArchiveFieldControl.INPUT,
                true,
                null,
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
                null,
                null);
    }
}
