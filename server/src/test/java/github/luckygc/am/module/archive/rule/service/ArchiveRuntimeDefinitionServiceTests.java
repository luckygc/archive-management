package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeVersionDataRepository;
import github.luckygc.am.module.archive.metadata.ArchiveFieldDataType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeFieldSource;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeStatus;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeActionDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeDefinitionDataRepository;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService.SaveArchiveRuntimeActionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService.SaveArchiveRuntimeDefinitionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeField;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("运行时定义状态边界")
class ArchiveRuntimeDefinitionServiceTests {

    private ArchiveRuntimeDefinitionDataRepository definitionRepository;
    private ArchiveRuntimeActionDataRepository actionRepository;
    private ArchiveGovernanceSchemeVersionDataRepository versionRepository;
    private ArchiveRuntimeDefinitionService service;

    @BeforeEach
    void setUp() {
        definitionRepository = mock(ArchiveRuntimeDefinitionDataRepository.class);
        actionRepository = mock(ArchiveRuntimeActionDataRepository.class);
        versionRepository = mock(ArchiveGovernanceSchemeVersionDataRepository.class);
        ArchiveRuntimeFieldCatalogService catalogService =
                mock(ArchiveRuntimeFieldCatalogService.class);
        when(versionRepository.findById(1L)).thenReturn(Optional.of(draftVersion()));
        when(catalogService.catalog(1L, "DOC", ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE))
                .thenReturn(catalog(ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE));
        when(catalogService.catalog(1L, "DOC", ArchiveRuntimeTriggerPoint.ITEM_BEFORE_DELETE))
                .thenReturn(catalog(ArchiveRuntimeTriggerPoint.ITEM_BEFORE_DELETE));
        when(definitionRepository.insert(any(ArchiveRuntimeDefinition.class)))
                .thenAnswer(
                        invocation -> {
                            ArchiveRuntimeDefinition definition = invocation.getArgument(0);
                            definition.setId(11L);
                            return definition;
                        });
        when(definitionRepository.update(any(ArchiveRuntimeDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service =
                new ArchiveRuntimeDefinitionService(
                        definitionRepository,
                        actionRepository,
                        versionRepository,
                        catalogService,
                        List.of(
                                new ArchiveRuntimeRejectActionHandler(),
                                new ArchiveRuntimeWarnActionHandler(),
                                new ArchiveRuntimeSetFieldActionHandler()),
                        JsonMapper.builder().build());
    }

    @Test
    @DisplayName("创建约束保存为草稿且不生成动作行")
    void createsConstraintDraft() {
        var response =
                service.createDefinition(
                        new SaveArchiveRuntimeDefinitionRequest(
                                1L,
                                ArchiveRuntimeDefinitionKind.CONSTRAINT,
                                "archive_no_required",
                                "档号必填",
                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                                null,
                                "DOC",
                                ArchiveLevel.ITEM,
                                10,
                                Map.of("field", "item.archiveNo", "operator", "IS_NOT_EMPTY"),
                                ArchiveRuntimeActionType.REJECT,
                                "档号不能为空",
                                true,
                                List.of()),
                        7L);

        assertThat(response.status()).isEqualTo(ArchiveRuntimeStatus.DRAFT);
        assertThat(response.definitionKind()).isEqualTo(ArchiveRuntimeDefinitionKind.CONSTRAINT);
        verify(definitionRepository).insert(any(ArchiveRuntimeDefinition.class));
    }

    @Test
    @DisplayName("创建规则拒绝不兼容触发点动作")
    void rejectsSetFieldOnDeleteTrigger() {
        assertThatThrownBy(
                        () ->
                                service.createDefinition(
                                        ruleRequest(
                                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_DELETE,
                                                "delete-rule"),
                                        7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不允许 SET_FIELD");
    }

    @Test
    @DisplayName("发布草稿记录字段签名发布人和发布时间")
    void publishesValidatedDraft() {
        ArchiveRuntimeDefinition definition = draftRule();
        when(definitionRepository.findById(11L)).thenReturn(Optional.of(definition));
        when(actionRepository.findByDefinitionId(11L)).thenReturn(List.of(setFieldAction(21L)));

        var response = service.publishDefinition(11L, 7L);

        assertThat(response.status()).isEqualTo(ArchiveRuntimeStatus.PUBLISHED);
        assertThat(response.fieldCatalogSignature()).isEqualTo("catalog-signature");
        assertThat(definition.getPublishedBy()).isEqualTo(7L);
        assertThat(definition.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("已发布定义不能修改或删除但可以停用")
    void publishedDefinitionOnlyAllowsEnableStateChange() {
        ArchiveRuntimeDefinition definition = draftRule();
        definition.setStatus(ArchiveRuntimeStatus.PUBLISHED);
        when(definitionRepository.findById(11L)).thenReturn(Optional.of(definition));
        when(actionRepository.findByDefinitionId(11L)).thenReturn(List.of(setFieldAction(21L)));

        assertThatThrownBy(
                        () ->
                                service.updateDefinition(
                                        11L,
                                        ruleRequest(
                                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                                                "set-year"),
                                        7L))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.deleteDefinition(11L, 7L))
                .isInstanceOf(BadRequestException.class);
        assertThat(service.updateEnabled(11L, false, 7L).enabled()).isFalse();
    }

    @Test
    @DisplayName("发布规则必须校验治理版本仍为草稿")
    void publishingRequiresDraftGovernanceVersion() {
        ArchiveGovernanceSchemeVersion published = draftVersion();
        published.setStatus(ArchiveGovernanceSchemeVersionStatus.PUBLISHED);
        when(versionRepository.findById(1L)).thenReturn(Optional.of(published));
        when(definitionRepository.findById(11L)).thenReturn(Optional.of(draftRule()));

        assertThatThrownBy(() -> service.publishDefinition(11L, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("草稿治理版本");
    }

    private SaveArchiveRuntimeDefinitionRequest ruleRequest(
            ArchiveRuntimeTriggerPoint triggerPoint, String code) {
        return new SaveArchiveRuntimeDefinitionRequest(
                1L,
                ArchiveRuntimeDefinitionKind.RULE,
                code,
                "设置年度",
                triggerPoint,
                null,
                "DOC",
                triggerPoint.archiveLevel(),
                0,
                Map.of("field", "item.archiveNo", "operator", "IS_NOT_EMPTY"),
                null,
                null,
                true,
                List.of(
                        new SaveArchiveRuntimeActionRequest(
                                ArchiveRuntimeActionType.SET_FIELD,
                                0,
                                Map.of("field", "item.archiveYear", "value", 2026))));
    }

    private ArchiveRuntimeDefinition draftRule() {
        ArchiveRuntimeDefinition definition = new ArchiveRuntimeDefinition();
        definition.setId(11L);
        definition.setSchemeVersionId(1L);
        definition.setDefinitionKind(ArchiveRuntimeDefinitionKind.RULE);
        definition.setDefinitionCode("set-year");
        definition.setDefinitionName("设置年度");
        definition.setTriggerPoint(ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE);
        definition.setScopeCategoryCode("DOC");
        definition.setScopeArchiveLevel(ArchiveLevel.ITEM);
        definition.setConditionJson(Map.of("field", "item.archiveNo", "operator", "IS_NOT_EMPTY"));
        definition.setStatus(ArchiveRuntimeStatus.DRAFT);
        return definition;
    }

    private ArchiveRuntimeAction setFieldAction(Long id) {
        ArchiveRuntimeAction action = new ArchiveRuntimeAction();
        action.setId(id);
        action.setDefinitionId(11L);
        action.setActionType(ArchiveRuntimeActionType.SET_FIELD);
        action.setActionParams(Map.of("field", "item.archiveYear", "value", 2026));
        return action;
    }

    private ArchiveGovernanceSchemeVersion draftVersion() {
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setId(1L);
        version.setStatus(ArchiveGovernanceSchemeVersionStatus.DRAFT);
        return version;
    }

    private ArchiveRuntimeFieldCatalog catalog(ArchiveRuntimeTriggerPoint triggerPoint) {
        return new ArchiveRuntimeFieldCatalog(
                1L,
                "DOC",
                triggerPoint,
                "catalog-signature",
                List.of(
                        field("item.archiveNo", ArchiveFieldDataType.TEXT, true),
                        field("item.archiveYear", ArchiveFieldDataType.INTEGER, true)));
    }

    private ArchiveRuntimeField field(String code, ArchiveFieldDataType type, boolean writable) {
        return new ArchiveRuntimeField(
                code, code, type, ArchiveRuntimeFieldSource.FIXED, true, writable, "DOC");
    }
}
