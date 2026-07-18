package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.metadata.ArchiveFieldDataType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeFieldSource;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeActionDataRepository;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeField;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("运行时确定性执行管线")
class ArchiveRuntimeExecutionServiceTests {

    private ArchiveRuleMapper ruleMapper;
    private ArchiveRuntimeActionDataRepository actionRepository;
    private ArchiveRuntimeExecutionService service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(ArchiveRuleMapper.class);
        actionRepository = mock(ArchiveRuntimeActionDataRepository.class);
        ArchiveRuntimeFieldCatalogService catalogService =
                mock(ArchiveRuntimeFieldCatalogService.class);
        when(catalogService.catalog(1L, "DOC", ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE))
                .thenReturn(catalog());
        service =
                new ArchiveRuntimeExecutionService(
                        ruleMapper,
                        actionRepository,
                        catalogService,
                        List.of(
                                new ArchiveRuntimeRejectActionHandler(),
                                new ArchiveRuntimeWarnActionHandler(),
                                new ArchiveRuntimeSetFieldActionHandler()),
                        JsonMapper.builder().build());
    }

    @Test
    @DisplayName("后续规则读取前序字段赋值且每条规则只执行一次")
    void laterRuleReadsPreviousAssignmentWithoutLooping() {
        ArchiveRuntimeDefinition setYear =
                rule(11L, "set-year", condition("item.archiveNo", "EQ", "A"));
        ArchiveRuntimeDefinition warn =
                rule(12L, "warn-year", condition("item.archiveYear", "EQ", 2026));
        when(ruleMapper.listExecutableRuntimeDefinitions(any())).thenReturn(List.of(setYear, warn));
        when(actionRepository.findByDefinitionId(11L))
                .thenReturn(List.of(setField(101L, "item.archiveYear", "2026")));
        when(actionRepository.findByDefinitionId(12L))
                .thenReturn(List.of(messageAction(102L, ArchiveRuntimeActionType.WARN, "年度已补齐")));

        var result = service.enforce(request(Map.of("item.archiveNo", "A")));

        assertThat(result.candidateFacts()).containsEntry("item.archiveYear", 2026L);
        assertThat(result.decisions())
                .extracting(decision -> decision.matched())
                .containsExactly(true, true);
        assertThat(result.warnings()).hasSize(1);
        verify(actionRepository).findByDefinitionId(11L);
        verify(actionRepository).findByDefinitionId(12L);
    }

    @Test
    @DisplayName("规则先修改候选值再执行最终约束")
    void constraintsRunAfterAllRules() {
        ArchiveRuntimeDefinition constraint =
                constraint(
                        13L,
                        "year-required",
                        condition("item.archiveYear", "EQ", 2026),
                        ArchiveRuntimeActionType.REJECT);
        ArchiveRuntimeDefinition setYear =
                rule(11L, "set-year", condition("item.archiveNo", "EQ", "A"));
        when(ruleMapper.listExecutableRuntimeDefinitions(any()))
                .thenReturn(List.of(constraint, setYear));
        when(actionRepository.findByDefinitionId(11L))
                .thenReturn(List.of(setField(101L, "item.archiveYear", 2026)));

        var result = service.enforce(request(Map.of("item.archiveNo", "A")));

        assertThat(result.blocking()).isFalse();
        assertThat(result.decisions())
                .extracting(decision -> decision.definitionCode())
                .containsExactly("set-year", "year-required");
    }

    @Test
    @DisplayName("WARN 约束断言失败形成非阻断警告")
    void failedWarnConstraintProducesWarning() {
        ArchiveRuntimeDefinition constraint =
                constraint(
                        13L,
                        "year-warning",
                        condition("item.archiveYear", "EQ", 2026),
                        ArchiveRuntimeActionType.WARN);
        when(ruleMapper.listExecutableRuntimeDefinitions(any())).thenReturn(List.of(constraint));

        var result = service.enforce(request(Map.of("item.archiveYear", 2025)));

        assertThat(result.blocking()).isFalse();
        assertThat(result.warnings())
                .extracting(warning -> warning.definitionCode())
                .containsExactly("year-warning");
    }

    @Test
    @DisplayName("同字段相同值幂等而不同值配置冲突")
    void repeatedAssignmentIsIdempotentButDifferentValueConflicts() {
        ArchiveRuntimeDefinition first = rule(11L, "first", condition("item.archiveNo", "EQ", "A"));
        ArchiveRuntimeDefinition second =
                rule(12L, "second", condition("item.archiveNo", "EQ", "A"));
        when(ruleMapper.listExecutableRuntimeDefinitions(any())).thenReturn(List.of(first, second));
        when(actionRepository.findByDefinitionId(11L))
                .thenReturn(List.of(setField(101L, "item.archiveYear", 2026)));
        when(actionRepository.findByDefinitionId(12L))
                .thenReturn(List.of(setField(102L, "item.archiveYear", "2026")));

        assertThat(service.enforce(request(Map.of("item.archiveNo", "A"))).blocking()).isFalse();

        when(actionRepository.findByDefinitionId(12L))
                .thenReturn(List.of(setField(102L, "item.archiveYear", 2027)));
        assertThatThrownBy(() -> service.enforce(request(Map.of("item.archiveNo", "A"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("item.archiveYear")
                .hasMessageContaining("first")
                .hasMessageContaining("second");
    }

    @Test
    @DisplayName("REJECT 动作形成阻断结果并由 enforce 统一失败")
    void rejectActionBlocksEnforcement() {
        ArchiveRuntimeDefinition reject =
                rule(11L, "reject-create", condition("item.archiveNo", "EQ", "A"));
        when(ruleMapper.listExecutableRuntimeDefinitions(any())).thenReturn(List.of(reject));
        when(actionRepository.findByDefinitionId(11L))
                .thenReturn(
                        List.of(messageAction(101L, ArchiveRuntimeActionType.REJECT, "不允许该档号")));

        assertThat(service.simulate(request(Map.of("item.archiveNo", "A"))).blocking()).isTrue();
        assertThatThrownBy(() -> service.enforce(request(Map.of("item.archiveNo", "A"))))
                .isInstanceOf(ArchiveRuntimeBlockedException.class);
    }

    private ArchiveRuntimeExecutionRequest request(Map<String, Object> facts) {
        return new ArchiveRuntimeExecutionRequest(
                1L,
                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE,
                "F001",
                "DOC",
                ArchiveLevel.ITEM,
                "ARCHIVE_ITEM",
                9L,
                facts,
                7L);
    }

    private ArchiveRuntimeDefinition rule(Long id, String code, Map<String, Object> condition) {
        ArchiveRuntimeDefinition definition = definition(id, code, condition);
        definition.setDefinitionKind(ArchiveRuntimeDefinitionKind.RULE);
        return definition;
    }

    private ArchiveRuntimeDefinition constraint(
            Long id,
            String code,
            Map<String, Object> condition,
            ArchiveRuntimeActionType actionType) {
        ArchiveRuntimeDefinition definition = definition(id, code, condition);
        definition.setDefinitionKind(ArchiveRuntimeDefinitionKind.CONSTRAINT);
        definition.setConstraintAction(actionType);
        definition.setConstraintMessage("年度必须为 2026");
        return definition;
    }

    private ArchiveRuntimeDefinition definition(
            Long id, String code, Map<String, Object> condition) {
        ArchiveRuntimeDefinition definition = new ArchiveRuntimeDefinition();
        definition.setId(id);
        definition.setDefinitionCode(code);
        definition.setTriggerPoint(ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE);
        definition.setConditionJson(condition);
        return definition;
    }

    private ArchiveRuntimeAction setField(Long id, String field, Object value) {
        ArchiveRuntimeAction action = new ArchiveRuntimeAction();
        action.setId(id);
        action.setActionType(ArchiveRuntimeActionType.SET_FIELD);
        action.setActionParams(Map.of("field", field, "value", value));
        return action;
    }

    private ArchiveRuntimeAction messageAction(
            Long id, ArchiveRuntimeActionType type, String message) {
        ArchiveRuntimeAction action = new ArchiveRuntimeAction();
        action.setId(id);
        action.setActionType(type);
        action.setActionParams(Map.of("message", message));
        return action;
    }

    private Map<String, Object> condition(String field, String operator, Object value) {
        return Map.of("field", field, "operator", operator, "value", value);
    }

    private ArchiveRuntimeFieldCatalog catalog() {
        return new ArchiveRuntimeFieldCatalog(
                1L,
                "DOC",
                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE,
                "signature",
                List.of(
                        new ArchiveRuntimeField(
                                "item.archiveNo",
                                "档号",
                                ArchiveFieldDataType.TEXT,
                                ArchiveRuntimeFieldSource.FIXED,
                                true,
                                true,
                                null),
                        new ArchiveRuntimeField(
                                "item.archiveYear",
                                "归档年度",
                                ArchiveFieldDataType.INTEGER,
                                ArchiveRuntimeFieldSource.FIXED,
                                true,
                                true,
                                null)));
    }
}
