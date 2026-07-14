package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyAttributeTypeDataRepository;
import github.luckygc.am.module.archive.rule.ArchiveRuleDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuleDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuleEffect;
import github.luckygc.am.module.archive.rule.ArchiveRuleEffectType;
import github.luckygc.am.module.archive.rule.ArchiveRuleStatus;
import github.luckygc.am.module.archive.rule.ArchiveRuleTrace;
import github.luckygc.am.module.archive.rule.ArchiveRuleType;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleDefinitionDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleEffectDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleTraceDataRepository;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("档案本地规则服务")
class ArchiveLocalRuleServiceTests {

    private ArchiveRuleDefinitionDataRepository ruleRepository;
    private ArchiveRuleEffectDataRepository effectRepository;
    private ArchiveRuleTraceDataRepository traceRepository;
    private ArchiveRuleMapper ruleMapper;
    private ArchiveOntologyAttributeTypeDataRepository attributeTypeRepository;
    private ArchiveDataScopeService dataScopeService;
    private ArchiveItemReadService archiveItemRoutingService;
    private ArchiveVolumeService archiveVolumeService;
    private ArchiveLocalRuleService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(ArchiveRuleDefinitionDataRepository.class);
        effectRepository = mock(ArchiveRuleEffectDataRepository.class);
        traceRepository = mock(ArchiveRuleTraceDataRepository.class);
        ruleMapper = mock(ArchiveRuleMapper.class);
        attributeTypeRepository = mock(ArchiveOntologyAttributeTypeDataRepository.class);
        dataScopeService = mock(ArchiveDataScopeService.class);
        archiveItemRoutingService = mock(ArchiveItemReadService.class);
        archiveVolumeService = mock(ArchiveVolumeService.class);
        ArchiveRuleTraceService traceService =
                new ArchiveRuleTraceService(
                        traceRepository,
                        ruleMapper,
                        dataScopeService,
                        archiveItemRoutingService,
                        archiveVolumeService);
        service =
                new ArchiveLocalRuleService(
                        ruleRepository,
                        effectRepository,
                        ruleMapper,
                        attributeTypeRepository,
                        traceService,
                        JsonMapper.builder().build());
    }

    @Test
    @DisplayName("创建规则时拒绝与规则类型不兼容的 effect")
    void createRuleShouldRejectIncompatibleEffect() {
        assertThatThrownBy(
                        () ->
                                service.createRule(
                                        new ArchiveLocalRuleService.CreateArchiveRuleRequest(
                                                1L,
                                                "validate_year",
                                                "校验年度",
                                                ArchiveRuleType.VALIDATION,
                                                "BEFORE_SAVE",
                                                null,
                                                null,
                                                null,
                                                ArchiveLevel.ITEM,
                                                null,
                                                1,
                                                Map.of(
                                                        "field", "fixed.archiveYear",
                                                        "operator", "GTE",
                                                        "value", 2020),
                                                true,
                                                List.of(
                                                        new ArchiveLocalRuleService
                                                                .CreateArchiveRuleEffectRequest(
                                                                ArchiveRuleEffectType.DENY_ACCESS,
                                                                0,
                                                                Map.of()))),
                                        7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("effect 与规则类型不兼容");
    }

    @Test
    @DisplayName("执行命中规则时返回阻断决策并保存追踪")
    void executeRulesShouldReturnBlockingDecisionAndTrace() {
        ArchiveRuleDefinition rule = rule(11L);
        when(ruleMapper.listExecutableRules(any())).thenReturn(List.of(rule));
        when(effectRepository.findByRuleId(11L))
                .thenReturn(List.of(effect(21L, ArchiveRuleEffectType.VALIDATION_ERROR)));
        when(traceRepository.insert(any(ArchiveRuleTrace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ArchiveRuleDecision> decisions =
                service.executeRules(
                        new ArchiveLocalRuleService.ExecuteArchiveRulesRequest(
                                1L,
                                "BEFORE_SAVE",
                                "F001",
                                "C001",
                                "ARCHIVE_ITEM",
                                ArchiveLevel.ITEM,
                                null,
                                Map.of("fixed.archiveYear", 2026),
                                false,
                                true,
                                7L));

        assertThat(decisions).hasSize(1);
        assertThat(decisions.getFirst().matched()).isTrue();
        assertThat(decisions.getFirst().blocking()).isTrue();
        ArgumentCaptor<ArchiveRuleTrace> captor = ArgumentCaptor.forClass(ArchiveRuleTrace.class);
        verify(traceRepository).insert(captor.capture());
        assertThat(captor.getValue().getRuleId()).isEqualTo(11L);
        assertThat(captor.getValue().isBlockingFlag()).isTrue();
    }

    @Test
    @DisplayName("保存规则追踪时脱敏 effect 参数中的敏感字段")
    void executeRulesShouldRedactSensitiveEffectParamsInTrace() {
        ArchiveRuleDefinition rule = rule(11L);
        when(ruleMapper.listExecutableRules(any())).thenReturn(List.of(rule));
        ArchiveRuleEffect effect = effect(21L, ArchiveRuleEffectType.VALIDATION_ERROR);
        effect.setEffectParams(
                Map.of(
                        "message",
                        "年度不合法",
                        "password",
                        "secret",
                        "nested",
                        Map.of("token", "token-value", "field", "archiveYear")));
        when(effectRepository.findByRuleId(11L)).thenReturn(List.of(effect));
        when(traceRepository.insert(any(ArchiveRuleTrace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.executeRules(
                new ArchiveLocalRuleService.ExecuteArchiveRulesRequest(
                        1L,
                        "BEFORE_SAVE",
                        "F001",
                        "C001",
                        "ARCHIVE_ITEM",
                        ArchiveLevel.ITEM,
                        null,
                        Map.of("fixed.archiveYear", 2026),
                        false,
                        true,
                        7L));

        ArgumentCaptor<ArchiveRuleTrace> captor = ArgumentCaptor.forClass(ArchiveRuleTrace.class);
        verify(traceRepository).insert(captor.capture());
        Map<String, Object> params =
                (Map<String, Object>) captor.getValue().getEffectJson().getFirst().get("params");
        assertThat(params.get("message")).isEqualTo("年度不合法");
        assertThat(params.get("password")).isEqualTo("[已脱敏]");
        assertThat((Map<String, Object>) params.get("nested"))
                .containsEntry("token", "[已脱敏]")
                .containsEntry("field", "archiveYear");
    }

    @Test
    @DisplayName("执行规则请求未提交事实时按空事实处理")
    void executeRulesShouldTreatMissingFactsAsEmptyMap() {
        ArchiveRuleDefinition rule = rule(11L);
        rule.setConditionJson(Map.of("field", "fixed.archiveNo", "operator", "IS_EMPTY"));
        when(ruleMapper.listExecutableRules(any())).thenReturn(List.of(rule));
        when(effectRepository.findByRuleId(11L))
                .thenReturn(List.of(effect(21L, ArchiveRuleEffectType.WARNING)));
        when(traceRepository.insert(any(ArchiveRuleTrace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ArchiveRuleDecision> decisions =
                service.executeRules(
                        new ArchiveLocalRuleService.ExecuteArchiveRulesRequest(
                                1L,
                                "BEFORE_SAVE",
                                "F001",
                                "C001",
                                "ARCHIVE_ITEM",
                                ArchiveLevel.ITEM,
                                null,
                                null,
                                false,
                                true,
                                7L));

        assertThat(decisions)
                .singleElement()
                .satisfies(decision -> assertThat(decision.matched()).isTrue());
        ArgumentCaptor<ArchiveRuleTrace> captor = ArgumentCaptor.forClass(ArchiveRuleTrace.class);
        verify(traceRepository).insert(captor.capture());
        assertThat(captor.getValue().getObjectId()).isNull();
    }

    @Test
    @DisplayName("查询规则追踪时过滤非本人创建的非档案对象追踪")
    void listRuleTracesShouldFilterProcessTracesCreatedByOtherUsers() {
        when(dataScopeService.resolveUserDataScope(7L)).thenReturn(ResolvedArchiveDataScope.none());
        when(ruleMapper.listRuleTraces(any()))
                .thenReturn(
                        List.of(
                                Map.of("id", 1L, "objectTypeCode", "FILING_BATCH", "createdBy", 8L),
                                Map.of(
                                        "id",
                                        2L,
                                        "objectTypeCode",
                                        "FILING_BATCH",
                                        "createdBy",
                                        7L)));

        List<Map<String, Object>> traces =
                service.listRuleTraces(
                        new ArchiveLocalRuleService.SearchArchiveRuleTracesRequest(
                                null, null, "FILING_BATCH", null, null, 100, 7L));

        assertThat(traces).extracting(trace -> trace.get("id")).containsExactly(2L);
    }

    @Test
    @DisplayName("查询档案条目规则追踪时按数据范围过滤")
    void listRuleTracesShouldFilterArchiveItemTracesByDataScope() {
        when(dataScopeService.resolveUserDataScope(7L)).thenReturn(ResolvedArchiveDataScope.none());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足"))
                .when(archiveItemRoutingService)
                .assertItemInDataScope(200L, 7L);
        when(ruleMapper.listRuleTraces(any()))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id",
                                        1L,
                                        "objectTypeCode",
                                        "ARCHIVE_ITEM",
                                        "objectId",
                                        100L,
                                        "createdBy",
                                        8L),
                                Map.of(
                                        "id",
                                        2L,
                                        "objectTypeCode",
                                        "ARCHIVE_ITEM",
                                        "objectId",
                                        200L,
                                        "createdBy",
                                        7L)));

        List<Map<String, Object>> traces =
                service.listRuleTraces(
                        new ArchiveLocalRuleService.SearchArchiveRuleTracesRequest(
                                null, null, "ARCHIVE_ITEM", null, null, 100, 7L));

        assertThat(traces).extracting(trace -> trace.get("id")).containsExactly(1L);
        verify(archiveItemRoutingService).assertItemInDataScope(100L, 7L);
        verify(archiveItemRoutingService).assertItemInDataScope(200L, 7L);
    }

    private ArchiveRuleDefinition rule(Long id) {
        ArchiveRuleDefinition rule = new ArchiveRuleDefinition();
        rule.setId(id);
        rule.setSchemeVersionId(1L);
        rule.setRuleCode("validate_year");
        rule.setRuleName("校验年度");
        rule.setRuleType(ArchiveRuleType.VALIDATION);
        rule.setTriggerCode("BEFORE_SAVE");
        rule.setScopeArchiveLevel(ArchiveLevel.ITEM);
        rule.setConditionJson(
                Map.of(
                        "field", "fixed.archiveYear",
                        "operator", "GTE",
                        "value", 2020));
        rule.setStatus(ArchiveRuleStatus.PUBLISHED);
        rule.setEnabled(true);
        return rule;
    }

    private ArchiveRuleEffect effect(Long id, ArchiveRuleEffectType effectType) {
        ArchiveRuleEffect effect = new ArchiveRuleEffect();
        effect.setId(id);
        effect.setRuleId(11L);
        effect.setEffectType(effectType);
        effect.setEffectOrder(0);
        effect.setEffectParams(Map.of("message", "年度不合法"));
        return effect;
    }
}
