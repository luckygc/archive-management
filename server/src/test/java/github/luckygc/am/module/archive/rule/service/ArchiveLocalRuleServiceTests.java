package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.mapper.ArchiveRuleTraceSearchCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
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
    private ArchiveCategoryService categoryService;
    private ArchiveLocalRuleService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(ArchiveRuleDefinitionDataRepository.class);
        effectRepository = mock(ArchiveRuleEffectDataRepository.class);
        traceRepository = mock(ArchiveRuleTraceDataRepository.class);
        ruleMapper = mock(ArchiveRuleMapper.class);
        attributeTypeRepository = mock(ArchiveOntologyAttributeTypeDataRepository.class);
        dataScopeService = mock(ArchiveDataScopeService.class);
        categoryService = mock(ArchiveCategoryService.class);
        ArchiveRuleTraceService traceService =
                new ArchiveRuleTraceService(
                        traceRepository, ruleMapper, dataScopeService, categoryService);
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
    @DisplayName("规则追踪一次编译分类范围并使用 limit 加一查询")
    void listRuleTracesShouldCompileScopeBeforePaging() {
        ResolvedArchiveDataScope resolved = ResolvedArchiveDataScope.none();
        List<ArchiveCategoryDto> categories = List.of(category(11L, "DOC"), category(12L, "PHOTO"));
        when(dataScopeService.resolveUserDataScope(7L)).thenReturn(resolved);
        when(categoryService.listCategories(null)).thenReturn(categories);
        when(dataScopeService.compileItemFilters(same(resolved), eq(categories), eq(null)))
                .thenReturn(
                        Map.of(
                                11L, ArchiveDataScopeFilter.fondsCodes(List.of("F001")),
                                12L, ArchiveDataScopeFilter.none()));
        when(ruleMapper.listRuleTraces(any())).thenReturn(List.of(trace(3L), trace(2L), trace(1L)));

        CursorPageResponse<Map<String, Object>> page =
                service.listRuleTraces(
                        new ArchiveLocalRuleService.SearchArchiveRuleTracesRequest(
                                null, null, null, null, null, 7L),
                        PageRequest.ofSize(2));

        assertThat(page.items()).extracting(row -> row.get("id")).containsExactly(3L, 2L);
        ArgumentCaptor<ArchiveRuleTraceSearchCriteria> captor =
                ArgumentCaptor.forClass(ArchiveRuleTraceSearchCriteria.class);
        verify(ruleMapper).listRuleTraces(captor.capture());
        assertThat(captor.getValue().page().rowLimit()).isEqualTo(3);
        assertThat(captor.getValue().itemScopes()).hasSize(1);
        verify(dataScopeService).resolveUserDataScope(7L);
        verify(categoryService).listCategories(null);
        verify(dataScopeService).compileItemFilters(same(resolved), eq(categories), eq(null));
        verify(dataScopeService, never()).buildItemFilter(any(), any(), any());
    }

    @Test
    @DisplayName("动态条件仅进入条目范围而不进入案卷范围")
    void listRuleTracesShouldExcludeDynamicConditionsFromVolumeScopes() {
        ResolvedArchiveDataScope resolved = ResolvedArchiveDataScope.none();
        List<ArchiveCategoryDto> categories = List.of(category(11L, "DOC"));
        when(dataScopeService.resolveUserDataScope(7L)).thenReturn(resolved);
        when(categoryService.listCategories(null)).thenReturn(categories);
        ArchiveDataScopeSqlGroup dynamicGroup =
                new ArchiveDataScopeSqlGroup(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new ArchiveSqlCondition(
                                        "status", ArchiveItemQueryOperator.EQ, "A")));
        when(dataScopeService.compileItemFilters(same(resolved), eq(categories), eq(null)))
                .thenReturn(Map.of(11L, ArchiveDataScopeFilter.groups(List.of(dynamicGroup))));
        when(ruleMapper.listRuleTraces(any())).thenReturn(List.of());

        service.listRuleTraces(searchRequest(7L), PageRequest.ofSize(20));

        ArgumentCaptor<ArchiveRuleTraceSearchCriteria> captor =
                ArgumentCaptor.forClass(ArchiveRuleTraceSearchCriteria.class);
        verify(ruleMapper).listRuleTraces(captor.capture());
        assertThat(captor.getValue().itemScopes().getFirst().groups())
                .containsExactly(dynamicGroup);
        assertThat(captor.getValue().volumeScopes()).isEmpty();
    }

    @Test
    @DisplayName("全量数据权限不枚举分类")
    void listRuleTracesShouldNotEnumerateCategoriesForAllData() {
        when(dataScopeService.resolveUserDataScope(7L)).thenReturn(ResolvedArchiveDataScope.all());
        when(ruleMapper.listRuleTraces(any())).thenReturn(List.of());

        service.listRuleTraces(searchRequest(7L), PageRequest.ofSize(20));

        verify(categoryService, never()).listCategories(null);
        verify(dataScopeService, never()).compileItemFilters(any(), any(), any());
        ArgumentCaptor<ArchiveRuleTraceSearchCriteria> captor =
                ArgumentCaptor.forClass(ArchiveRuleTraceSearchCriteria.class);
        verify(ruleMapper).listRuleTraces(captor.capture());
        assertThat(captor.getValue().allData()).isTrue();
    }

    @Test
    @DisplayName("规则追踪解析创建时间和 ID 双字段 cursor")
    void listRuleTracesShouldParseStableCursor() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 15, 10, 30);
        when(dataScopeService.resolveUserDataScope(7L)).thenReturn(ResolvedArchiveDataScope.all());
        when(ruleMapper.listRuleTraces(any())).thenReturn(List.of());

        service.listRuleTraces(
                searchRequest(7L),
                PageRequest.ofSize(20).afterCursor(PageRequest.Cursor.forKey(createdAt, 99L)));

        ArgumentCaptor<ArchiveRuleTraceSearchCriteria> captor =
                ArgumentCaptor.forClass(ArchiveRuleTraceSearchCriteria.class);
        verify(ruleMapper).listRuleTraces(captor.capture());
        assertThat(captor.getValue().page().cursorCreatedAt()).isEqualTo(createdAt);
        assertThat(captor.getValue().page().cursorId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("上一页查询反转数据库升序结果并恢复展示顺序")
    void listRuleTracesShouldReversePreviousPageRows() {
        LocalDateTime cursorTime = LocalDateTime.of(2026, 7, 15, 9, 0);
        when(dataScopeService.resolveUserDataScope(7L)).thenReturn(ResolvedArchiveDataScope.all());
        when(ruleMapper.listRuleTraces(any())).thenReturn(List.of(trace(1L), trace(2L), trace(3L)));

        CursorPageResponse<Map<String, Object>> page =
                service.listRuleTraces(
                        searchRequest(7L),
                        PageRequest.ofSize(2)
                                .beforeCursor(PageRequest.Cursor.forKey(cursorTime, 99L)));

        assertThat(page.items()).extracting(row -> row.get("id")).containsExactly(2L, 1L);
        ArgumentCaptor<ArchiveRuleTraceSearchCriteria> captor =
                ArgumentCaptor.forClass(ArchiveRuleTraceSearchCriteria.class);
        verify(ruleMapper).listRuleTraces(captor.capture());
        assertThat(captor.getValue().page().previous()).isTrue();
    }

    @Test
    @DisplayName("规则追踪拒绝结构无效的 cursor")
    void listRuleTracesShouldRejectInvalidCursor() {
        when(dataScopeService.resolveUserDataScope(7L)).thenReturn(ResolvedArchiveDataScope.all());

        assertThatThrownBy(
                        () ->
                                service.listRuleTraces(
                                        searchRequest(7L),
                                        PageRequest.ofSize(20)
                                                .afterCursor(
                                                        PageRequest.Cursor.forKey("bad", 99L))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页 cursor 无效");
    }

    private ArchiveLocalRuleService.SearchArchiveRuleTracesRequest searchRequest(Long userId) {
        return new ArchiveLocalRuleService.SearchArchiveRuleTracesRequest(
                null, null, null, null, null, userId);
    }

    private Map<String, Object> trace(Long id) {
        return Map.of("id", id, "createdAt", LocalDateTime.of(2026, 7, 15, 10, 0).plusSeconds(id));
    }

    private ArchiveCategoryDto category(Long id, String code) {
        LocalDateTime time = LocalDateTime.of(2026, 7, 1, 10, 0);
        return new ArchiveCategoryDto(
                id,
                1L,
                null,
                code,
                code + "档案",
                ArchiveManagementMode.VOLUME_ITEM,
                null,
                "am_archive_item_data_" + code.toLowerCase(),
                null,
                null,
                ArchiveTableStatus.BUILT,
                time,
                true,
                0,
                time,
                time);
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
