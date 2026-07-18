package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeStatus;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeActionDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeDefinitionDataRepository;

@DisplayName("运行时字段反向引用保护")
class ArchiveRuntimeFieldReferenceIntegrationTests {

    @Test
    @DisplayName("已发布条件引用阻止字段删除停用改名和改型")
    void publishedConditionProtectsField() {
        ArchiveRuntimeDefinitionDataRepository definitions =
                mock(ArchiveRuntimeDefinitionDataRepository.class);
        ArchiveRuntimeActionDataRepository actions = mock(ArchiveRuntimeActionDataRepository.class);
        ArchiveRuntimeDefinition definition = definition("DOC", "metadata.title");
        when(definitions.findByStatus(ArchiveRuntimeStatus.PUBLISHED))
                .thenReturn(List.of(definition));
        ArchiveRuntimeFieldReferenceService service =
                new ArchiveRuntimeFieldReferenceService(definitions, actions);

        assertThatThrownBy(
                        () ->
                                service.requireDeleteAllowed(
                                        "DOC", ArchiveFieldScope.METADATA, "title"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("title-required");
        assertThatThrownBy(
                        () ->
                                service.requireUpdateAllowed(
                                        "DOC",
                                        ArchiveLevel.ITEM,
                                        ArchiveFieldScope.METADATA,
                                        "title",
                                        ArchiveFieldType.TEXT,
                                        true,
                                        true,
                                        ArchiveLevel.ITEM,
                                        ArchiveFieldScope.METADATA,
                                        "renamed",
                                        ArchiveFieldType.TEXT,
                                        true,
                                        true))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("已发布 SET_FIELD 引用阻止字段变为只读")
    void publishedActionProtectsWritableField() {
        ArchiveRuntimeDefinitionDataRepository definitions =
                mock(ArchiveRuntimeDefinitionDataRepository.class);
        ArchiveRuntimeActionDataRepository actions = mock(ArchiveRuntimeActionDataRepository.class);
        ArchiveRuntimeDefinition definition = definition("DOC", "item.archiveNo");
        definition.setConditionJson(Map.of("field", "item.archiveNo", "operator", "IS_NOT_EMPTY"));
        ArchiveRuntimeAction action = new ArchiveRuntimeAction();
        action.setActionParams(Map.of("field", "physical.boxNo", "value", 1));
        when(definitions.findByStatus(ArchiveRuntimeStatus.PUBLISHED))
                .thenReturn(List.of(definition));
        when(actions.findByDefinitionId(11L)).thenReturn(List.of(action));
        ArchiveRuntimeFieldReferenceService service =
                new ArchiveRuntimeFieldReferenceService(definitions, actions);

        assertThatThrownBy(
                        () ->
                                service.requireUpdateAllowed(
                                        "DOC",
                                        ArchiveLevel.ITEM,
                                        ArchiveFieldScope.PHYSICAL,
                                        "boxNo",
                                        ArchiveFieldType.INTEGER,
                                        true,
                                        true,
                                        ArchiveLevel.ITEM,
                                        ArchiveFieldScope.PHYSICAL,
                                        "boxNo",
                                        ArchiveFieldType.INTEGER,
                                        true,
                                        false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("其他分类引用和非破坏性展示修改不阻塞")
    void unrelatedOrNonDestructiveChangeIsAllowed() {
        ArchiveRuntimeDefinitionDataRepository definitions =
                mock(ArchiveRuntimeDefinitionDataRepository.class);
        ArchiveRuntimeActionDataRepository actions = mock(ArchiveRuntimeActionDataRepository.class);
        when(definitions.findByStatus(ArchiveRuntimeStatus.PUBLISHED))
                .thenReturn(List.of(definition("PHOTO", "metadata.title")));
        ArchiveRuntimeFieldReferenceService service =
                new ArchiveRuntimeFieldReferenceService(definitions, actions);

        assertThatCode(
                        () ->
                                service.requireDeleteAllowed(
                                        "DOC", ArchiveFieldScope.METADATA, "title"))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                service.requireUpdateAllowed(
                                        "DOC",
                                        ArchiveLevel.ITEM,
                                        ArchiveFieldScope.METADATA,
                                        "title",
                                        ArchiveFieldType.TEXT,
                                        true,
                                        true,
                                        ArchiveLevel.ITEM,
                                        ArchiveFieldScope.METADATA,
                                        "title",
                                        ArchiveFieldType.TEXT,
                                        true,
                                        true))
                .doesNotThrowAnyException();
    }

    private ArchiveRuntimeDefinition definition(String categoryCode, String fieldCode) {
        ArchiveRuntimeDefinition definition = new ArchiveRuntimeDefinition();
        definition.setId(11L);
        definition.setDefinitionCode("title-required");
        definition.setScopeCategoryCode(categoryCode);
        definition.setConditionJson(Map.of("field", fieldCode, "operator", "IS_NOT_EMPTY"));
        return definition;
    }
}
