package github.luckygc.am.module.archive.rule.service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.mapper.ArchiveRuleExecutionCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.mapper.ArchiveRuleTraceSearchCriteria;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyAttributeTypeDataRepository;
import github.luckygc.am.module.archive.rule.ArchiveRuleDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuleDecisionSeverity;
import github.luckygc.am.module.archive.rule.ArchiveRuleDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuleEffect;
import github.luckygc.am.module.archive.rule.ArchiveRuleEffectDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuleEffectType;
import github.luckygc.am.module.archive.rule.ArchiveRuleStatus;
import github.luckygc.am.module.archive.rule.ArchiveRuleTrace;
import github.luckygc.am.module.archive.rule.ArchiveRuleType;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleDefinitionDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleEffectDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleTraceDataRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ArchiveLocalRuleService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");
    private static final Pattern SENSITIVE_PARAM_KEY_PATTERN =
            Pattern.compile(
                    ".*(password|passwd|secret|token|credential|authorization|cookie|idcard|identity|certificate|phone|mobile|email|address|bank|account|summary|content|fulltext).*",
                    Pattern.CASE_INSENSITIVE);
    private static final String REDACTED_VALUE = "[已脱敏]";
    private static final Map<ArchiveRuleType, Set<ArchiveRuleEffectType>> EFFECTS_BY_RULE_TYPE =
            createEffectCompatibility();

    private final ArchiveRuleDefinitionDataRepository ruleRepository;
    private final ArchiveRuleEffectDataRepository effectRepository;
    private final ArchiveRuleTraceDataRepository traceRepository;
    private final ArchiveRuleMapper ruleMapper;
    private final ArchiveRuleFactResolver factResolver;
    private final ArchiveRuleConditionEvaluator conditionEvaluator =
            new ArchiveRuleConditionEvaluator();
    private final ArchiveDataScopeService dataScopeService;
    private final ArchiveItemRoutingService archiveItemRoutingService;
    private final ArchiveVolumeService archiveVolumeService;
    private final JsonMapper jsonMapper;

    public ArchiveLocalRuleService(
            ArchiveRuleDefinitionDataRepository ruleRepository,
            ArchiveRuleEffectDataRepository effectRepository,
            ArchiveRuleTraceDataRepository traceRepository,
            ArchiveRuleMapper ruleMapper,
            ArchiveOntologyAttributeTypeDataRepository attributeTypeRepository,
            ArchiveDataScopeService dataScopeService,
            ArchiveItemRoutingService archiveItemRoutingService,
            ArchiveVolumeService archiveVolumeService,
            JsonMapper jsonMapper) {
        this.ruleRepository = ruleRepository;
        this.effectRepository = effectRepository;
        this.traceRepository = traceRepository;
        this.ruleMapper = ruleMapper;
        this.factResolver = new ArchiveRuleFactResolver(attributeTypeRepository);
        this.dataScopeService = dataScopeService;
        this.archiveItemRoutingService = archiveItemRoutingService;
        this.archiveVolumeService = archiveVolumeService;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public List<ArchiveRuleResponse> listRules(
            Long schemeVersionId, @Nullable ArchiveRuleStatus status) {
        return (status == null
                        ? ruleRepository.findBySchemeVersionId(schemeVersionId)
                        : ruleRepository.findBySchemeVersionIdAndStatus(schemeVersionId, status))
                .stream()
                        .map(
                                rule ->
                                        toRuleResponse(
                                                rule, effectRepository.findByRuleId(rule.getId())))
                        .toList();
    }

    @Transactional
    public ArchiveRuleResponse createRule(CreateArchiveRuleRequest request, Long userId) {
        String ruleCode = normalizeCode(request.ruleCode(), "ruleCode", "规则编码不能为空");
        String ruleName = requiredText(request.ruleName(), "ruleName", "规则名称不能为空");
        String triggerCode = requiredText(request.triggerCode(), "triggerCode", "触发点不能为空");
        if (request.ruleType() == null) {
            throw new BadRequestException("规则类型不能为空", "ruleType", "规则类型不能为空");
        }
        if (ruleRepository.findBySchemeVersionIdAndRuleCode(request.schemeVersionId(), ruleCode)
                != null) {
            throw new BadRequestException("规则编码已存在", "ruleCode", "规则编码已存在");
        }
        JsonNode condition = toConditionNode(request.conditionJson());
        validateRuleDefinition(request.ruleType(), condition, request.effects());
        ArchiveRuleDefinition rule = new ArchiveRuleDefinition();
        applyRuleFields(rule, request, ruleCode, ruleName, triggerCode);
        rule.setStatus(ArchiveRuleStatus.DRAFT);
        ArchiveRuleDefinition saved = ruleRepository.insert(rule);
        List<ArchiveRuleEffect> effects = insertEffects(saved.getId(), request.effects());
        return toRuleResponse(saved, effects);
    }

    @Transactional
    public ArchiveRuleResponse publishRule(Long ruleId, Long userId) {
        ArchiveRuleDefinition rule = loadRule(ruleId);
        if (rule.getStatus() != ArchiveRuleStatus.DRAFT) {
            throw new BadRequestException("只有草稿规则可以发布");
        }
        List<ArchiveRuleEffect> effects = effectRepository.findByRuleId(ruleId);
        validateRuleDefinition(
                rule.getRuleType(),
                toConditionNode(rule.getConditionJson()),
                toEffectRequests(effects));
        rule.setStatus(ArchiveRuleStatus.PUBLISHED);
        rule.setPublishedBy(userId);
        rule.setPublishedAt(LocalDateTime.now());
        return toRuleResponse(ruleRepository.update(rule), effects);
    }

    @Transactional
    public ArchiveRuleResponse updateRuleEnabled(Long ruleId, boolean enabled, Long userId) {
        ArchiveRuleDefinition rule = loadRule(ruleId);
        rule.setEnabled(enabled);
        return toRuleResponse(ruleRepository.update(rule), effectRepository.findByRuleId(ruleId));
    }

    @Transactional
    public void deleteRule(Long ruleId, Long userId) {
        ArchiveRuleDefinition rule = loadRule(ruleId);
        if (rule.getStatus() != ArchiveRuleStatus.DRAFT) {
            throw new BadRequestException("已发布规则不能删除，请先停用");
        }
        for (ArchiveRuleEffect effect : effectRepository.findByRuleId(ruleId)) {
            effectRepository.update(effect);
            effectRepository.delete(effect);
        }
        ruleRepository.update(rule);
        ruleRepository.delete(rule);
    }

    @Transactional
    public List<ArchiveRuleDecision> executeRules(ExecuteArchiveRulesRequest request) {
        ArchiveRuleExecutionCriteria criteria =
                new ArchiveRuleExecutionCriteria(
                        request.schemeVersionId(),
                        requiredText(request.triggerCode(), "triggerCode", "触发点不能为空"),
                        StringUtils.trimToNull(request.fondsCode()),
                        StringUtils.trimToNull(request.categoryCode()),
                        StringUtils.trimToNull(request.objectTypeCode()),
                        request.archiveLevel() == null ? null : request.archiveLevel().name(),
                        StringUtils.trimToNull(request.eventCode()));
        List<ArchiveRuleDecision> decisions =
                ruleMapper.listExecutableRules(criteria).stream()
                        .map(rule -> executeRule(rule, request))
                        .filter(decision -> decision.matched() || request.includeSkipped())
                        .toList();
        if (request.recordTrace()) {
            decisions.forEach(decision -> saveTrace(request, decision));
        }
        return decisions;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuleTraces(SearchArchiveRuleTracesRequest request) {
        Long userId = AuthenticatedUsers.requireResolvedUserId(request.userId());
        int limit = request.limit() == null ? 100 : Math.clamp(request.limit(), 1, 500);
        boolean allData = dataScopeService.resolveUserDataScope(userId).allData();
        return ruleMapper
                .listRuleTraces(
                        new ArchiveRuleTraceSearchCriteria(
                                request.schemeVersionId(),
                                StringUtils.trimToNull(request.triggerCode()),
                                StringUtils.trimToNull(request.objectTypeCode()),
                                request.objectId(),
                                request.ruleType() == null ? null : request.ruleType().name(),
                                500))
                .stream()
                .filter(trace -> traceVisible(trace, userId, allData))
                .limit(limit)
                .toList();
    }

    private ArchiveRuleDecision executeRule(
            ArchiveRuleDefinition rule, ExecuteArchiveRulesRequest request) {
        JsonNode condition = toConditionNode(rule.getConditionJson());
        new ArchiveRuleConditionValidator(factResolver).validate(condition);
        boolean matched = conditionEvaluator.evaluate(condition, request.facts());
        List<ArchiveRuleEffectDecision> effects =
                matched
                        ? effectRepository.findByRuleId(rule.getId()).stream()
                                .map(this::toEffectDecision)
                                .toList()
                        : List.of();
        boolean blocking =
                effects.stream()
                        .anyMatch(
                                effect ->
                                        effect.effectType()
                                                        == ArchiveRuleEffectType.VALIDATION_ERROR
                                                || effect.effectType()
                                                        == ArchiveRuleEffectType.DENY_ACCESS);
        ArchiveRuleDecisionSeverity severity = severity(effects);
        return new ArchiveRuleDecision(
                rule.getId(),
                rule.getRuleCode(),
                rule.getRuleType(),
                matched,
                effects,
                matched ? decisionMessage(effects) : null,
                severity,
                blocking,
                matched ? null : "规则条件未命中");
    }

    private void saveTrace(ExecuteArchiveRulesRequest request, ArchiveRuleDecision decision) {
        ArchiveRuleTrace trace = new ArchiveRuleTrace();
        trace.setSchemeVersionId(request.schemeVersionId());
        trace.setTriggerCode(request.triggerCode());
        trace.setObjectTypeCode(StringUtils.defaultString(request.objectTypeCode(), "UNKNOWN"));
        trace.setObjectId(request.objectId());
        trace.setRuleId(decision.ruleId());
        trace.setRuleCode(decision.ruleCode());
        trace.setRuleType(decision.ruleType());
        trace.setMatchedFlag(decision.matched());
        trace.setBlockingFlag(decision.blocking());
        trace.setEffectJson(
                decision.effects().stream()
                        .map(
                                effect -> {
                                    Map<String, Object> effectTrace = new LinkedHashMap<>();
                                    effectTrace.put("effectType", effect.effectType().name());
                                    effectTrace.put(
                                            "params", redactSensitiveParams(effect.params()));
                                    return effectTrace;
                                })
                        .toList());
        trace.setMessage(decision.message());
        trace.setSeverity(decision.severity());
        trace.setSkippedReason(decision.skippedReason());
        trace.setCreatedBy(request.userId());
        traceRepository.insert(trace);
    }

    private boolean traceVisible(Map<String, Object> trace, Long userId, boolean allData) {
        if (allData) {
            return true;
        }
        String objectTypeCode = string(trace, "objectTypeCode");
        Long objectId = longOrNull(trace, "objectId");
        if ("ARCHIVE_ITEM".equals(objectTypeCode) && objectId != null) {
            return archiveItemVisible(objectId, userId);
        }
        if ("ARCHIVE_VOLUME".equals(objectTypeCode) && objectId != null) {
            return archiveVolumeVisible(objectId, userId);
        }
        return Objects.equals(longOrNull(trace, "createdBy"), userId);
    }

    private boolean archiveItemVisible(Long objectId, Long userId) {
        try {
            archiveItemRoutingService.assertItemInDataScope(objectId, userId);
            return true;
        } catch (ResponseStatusException exception) {
            return nonVisibleArchiveObject(exception);
        }
    }

    private boolean archiveVolumeVisible(Long objectId, Long userId) {
        try {
            archiveVolumeService.assertVolumeInDataScope(objectId, userId);
            return true;
        } catch (ResponseStatusException exception) {
            return nonVisibleArchiveObject(exception);
        }
    }

    private boolean nonVisibleArchiveObject(ResponseStatusException exception) {
        if (exception.getStatusCode() == HttpStatus.FORBIDDEN
                || exception.getStatusCode() == HttpStatus.NOT_FOUND) {
            return false;
        }
        throw exception;
    }

    private Map<String, Object> redactSensitiveParams(Map<String, Object> params) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        params.forEach((key, value) -> redacted.put(key, redactSensitiveValue(key, value)));
        return redacted;
    }

    private @Nullable Object redactSensitiveValue(String key, @Nullable Object value) {
        if (SENSITIVE_PARAM_KEY_PATTERN.matcher(key).matches()) {
            return REDACTED_VALUE;
        }
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            nestedMap.forEach(
                    (nestedKey, nestedValue) ->
                            redacted.put(
                                    String.valueOf(nestedKey),
                                    redactSensitiveValue(String.valueOf(nestedKey), nestedValue)));
            return redacted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> redactListItem(key, item)).toList();
        }
        return value;
    }

    private @Nullable Object redactListItem(String key, @Nullable Object item) {
        if (item instanceof Map<?, ?> nestedMap) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            nestedMap.forEach(
                    (nestedKey, nestedValue) ->
                            redacted.put(
                                    String.valueOf(nestedKey),
                                    redactSensitiveValue(String.valueOf(nestedKey), nestedValue)));
            return redacted;
        }
        return redactSensitiveValue(key, item);
    }

    private @Nullable String string(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        return value == null ? null : String.valueOf(value);
    }

    private @Nullable Long longOrNull(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private @Nullable Object rowValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null) {
            return value;
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    private void validateRuleDefinition(
            ArchiveRuleType ruleType,
            JsonNode condition,
            List<CreateArchiveRuleEffectRequest> effects) {
        new ArchiveRuleConditionValidator(factResolver).validate(condition);
        if (effects == null || effects.isEmpty()) {
            throw new BadRequestException("规则 effect 不能为空");
        }
        for (CreateArchiveRuleEffectRequest effect : effects) {
            if (effect.effectType() == null) {
                throw new BadRequestException("规则 effect 类型不能为空");
            }
            if (!EFFECTS_BY_RULE_TYPE
                    .getOrDefault(ruleType, Set.of())
                    .contains(effect.effectType())) {
                throw new BadRequestException("effect 与规则类型不兼容");
            }
        }
    }

    private void applyRuleFields(
            ArchiveRuleDefinition rule,
            CreateArchiveRuleRequest request,
            String ruleCode,
            String ruleName,
            String triggerCode) {
        rule.setSchemeVersionId(request.schemeVersionId());
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleName);
        rule.setRuleType(request.ruleType());
        rule.setTriggerCode(triggerCode);
        rule.setScopeFondsCode(StringUtils.trimToNull(request.scopeFondsCode()));
        rule.setScopeCategoryCode(StringUtils.trimToNull(request.scopeCategoryCode()));
        rule.setScopeObjectTypeId(request.scopeObjectTypeId());
        rule.setScopeArchiveLevel(request.scopeArchiveLevel());
        rule.setScopeEventTypeId(request.scopeEventTypeId());
        rule.setPriority(request.priority() == null ? 0 : request.priority());
        rule.setConditionJson(request.conditionJson());
        rule.setEnabled(request.enabled() == null || request.enabled());
    }

    private List<ArchiveRuleEffect> insertEffects(
            Long ruleId, List<CreateArchiveRuleEffectRequest> requests) {
        List<ArchiveRuleEffect> effects =
                requests.stream().map(request -> toEffect(ruleId, request)).toList();
        return effectRepository.insertAll(effects);
    }

    private ArchiveRuleEffect toEffect(Long ruleId, CreateArchiveRuleEffectRequest request) {
        ArchiveRuleEffect effect = new ArchiveRuleEffect();
        effect.setRuleId(ruleId);
        effect.setEffectType(request.effectType());
        effect.setEffectOrder(request.effectOrder() == null ? 0 : request.effectOrder());
        effect.setEffectParams(request.effectParams() == null ? Map.of() : request.effectParams());
        return effect;
    }

    private ArchiveRuleEffectDecision toEffectDecision(ArchiveRuleEffect effect) {
        return new ArchiveRuleEffectDecision(effect.getEffectType(), effect.getEffectParams());
    }

    private List<CreateArchiveRuleEffectRequest> toEffectRequests(List<ArchiveRuleEffect> effects) {
        return effects.stream()
                .map(
                        effect ->
                                new CreateArchiveRuleEffectRequest(
                                        effect.getEffectType(),
                                        effect.getEffectOrder(),
                                        effect.getEffectParams()))
                .toList();
    }

    private JsonNode toConditionNode(Map<String, Object> condition) {
        try {
            return jsonMapper.readTree(jsonMapper.writeValueAsString(condition));
        } catch (Exception ex) {
            throw new BadRequestException("规则条件不是有效 JSON 结构");
        }
    }

    private ArchiveRuleDefinition loadRule(Long ruleId) {
        return ruleRepository.findById(ruleId).orElseThrow(() -> notFound("规则不存在"));
    }

    private String normalizeCode(@Nullable String value, String field, String message) {
        String code = StringUtils.trimToNull(value);
        if (code == null) {
            throw new BadRequestException(message, field, message);
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BadRequestException("规则编码必须为小写 snake_case 或 kebab-case", field, "规则编码格式不合法");
        }
        return code;
    }

    private String requiredText(@Nullable String value, String field, String message) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            throw new BadRequestException(message, field, message);
        }
        return text;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ArchiveRuleResponse toRuleResponse(
            ArchiveRuleDefinition rule, List<ArchiveRuleEffect> effects) {
        return new ArchiveRuleResponse(
                rule.getId(),
                rule.getSchemeVersionId(),
                rule.getRuleCode(),
                rule.getRuleName(),
                rule.getRuleType(),
                rule.getTriggerCode(),
                rule.getStatus(),
                rule.isEnabled(),
                rule.getPriority(),
                effects.stream().map(this::toEffectResponse).toList());
    }

    private ArchiveRuleEffectResponse toEffectResponse(ArchiveRuleEffect effect) {
        return new ArchiveRuleEffectResponse(
                effect.getId(),
                effect.getEffectType(),
                effect.getEffectOrder(),
                effect.getEffectParams());
    }

    private ArchiveRuleDecisionSeverity severity(List<ArchiveRuleEffectDecision> effects) {
        if (effects.stream()
                .anyMatch(
                        effect ->
                                effect.effectType() == ArchiveRuleEffectType.VALIDATION_ERROR
                                        || effect.effectType()
                                                == ArchiveRuleEffectType.DENY_ACCESS)) {
            return ArchiveRuleDecisionSeverity.ERROR;
        }
        if (effects.stream()
                .anyMatch(effect -> effect.effectType() == ArchiveRuleEffectType.WARNING)) {
            return ArchiveRuleDecisionSeverity.WARNING;
        }
        return ArchiveRuleDecisionSeverity.INFO;
    }

    private @Nullable String decisionMessage(List<ArchiveRuleEffectDecision> effects) {
        return effects.stream()
                .map(effect -> effect.params().get("message"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .findFirst()
                .orElse(null);
    }

    private static Map<ArchiveRuleType, Set<ArchiveRuleEffectType>> createEffectCompatibility() {
        Map<ArchiveRuleType, Set<ArchiveRuleEffectType>> compatibility =
                new EnumMap<>(ArchiveRuleType.class);
        compatibility.put(
                ArchiveRuleType.VALIDATION,
                Set.of(
                        ArchiveRuleEffectType.VALIDATION_ERROR,
                        ArchiveRuleEffectType.WARNING,
                        ArchiveRuleEffectType.REQUIRE_REVIEW));
        compatibility.put(
                ArchiveRuleType.DERIVATION,
                Set.of(
                        ArchiveRuleEffectType.SUGGEST_VALUE,
                        ArchiveRuleEffectType.DERIVED_VALUE,
                        ArchiveRuleEffectType.WARNING));
        compatibility.put(
                ArchiveRuleType.REFERENCE_CODE,
                Set.of(
                        ArchiveRuleEffectType.SUGGEST_VALUE,
                        ArchiveRuleEffectType.DERIVED_VALUE,
                        ArchiveRuleEffectType.VALIDATION_ERROR,
                        ArchiveRuleEffectType.WARNING));
        compatibility.put(
                ArchiveRuleType.RETENTION,
                Set.of(
                        ArchiveRuleEffectType.SUGGEST_VALUE,
                        ArchiveRuleEffectType.DERIVED_VALUE,
                        ArchiveRuleEffectType.WARNING));
        compatibility.put(
                ArchiveRuleType.ACCESS,
                Set.of(
                        ArchiveRuleEffectType.DENY_ACCESS,
                        ArchiveRuleEffectType.MASK_FIELD,
                        ArchiveRuleEffectType.WARNING));
        compatibility.put(
                ArchiveRuleType.QUALITY,
                Set.of(
                        ArchiveRuleEffectType.REQUIRE_QUALITY_CHECK,
                        ArchiveRuleEffectType.WARNING,
                        ArchiveRuleEffectType.VALIDATION_ERROR));
        compatibility.put(
                ArchiveRuleType.TRANSFER,
                Set.of(
                        ArchiveRuleEffectType.REQUIRE_REVIEW,
                        ArchiveRuleEffectType.WARNING,
                        ArchiveRuleEffectType.VALIDATION_ERROR));
        compatibility.put(
                ArchiveRuleType.FILING,
                Set.of(
                        ArchiveRuleEffectType.REQUIRE_REVIEW,
                        ArchiveRuleEffectType.WARNING,
                        ArchiveRuleEffectType.VALIDATION_ERROR));
        compatibility.put(
                ArchiveRuleType.EXPORT,
                Set.of(
                        ArchiveRuleEffectType.INCLUDE_IN_PACKAGE,
                        ArchiveRuleEffectType.WARNING,
                        ArchiveRuleEffectType.DENY_ACCESS,
                        ArchiveRuleEffectType.MASK_FIELD));
        return compatibility;
    }

    public record CreateArchiveRuleRequest(
            Long schemeVersionId,
            String ruleCode,
            String ruleName,
            ArchiveRuleType ruleType,
            String triggerCode,
            @Nullable String scopeFondsCode,
            @Nullable String scopeCategoryCode,
            @Nullable Long scopeObjectTypeId,
            @Nullable ArchiveLevel scopeArchiveLevel,
            @Nullable Long scopeEventTypeId,
            @Nullable Integer priority,
            Map<String, Object> conditionJson,
            @Nullable Boolean enabled,
            List<CreateArchiveRuleEffectRequest> effects) {}

    public record CreateArchiveRuleEffectRequest(
            ArchiveRuleEffectType effectType,
            @Nullable Integer effectOrder,
            @Nullable Map<String, Object> effectParams) {}

    public record ArchiveRuleResponse(
            Long id,
            Long schemeVersionId,
            String ruleCode,
            String ruleName,
            ArchiveRuleType ruleType,
            String triggerCode,
            ArchiveRuleStatus status,
            boolean enabled,
            int priority,
            List<ArchiveRuleEffectResponse> effects) {}

    public record ArchiveRuleEffectResponse(
            Long id,
            ArchiveRuleEffectType effectType,
            int effectOrder,
            Map<String, Object> effectParams) {}

    public record ExecuteArchiveRulesRequest(
            Long schemeVersionId,
            String triggerCode,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            @Nullable String objectTypeCode,
            @Nullable ArchiveLevel archiveLevel,
            @Nullable String eventCode,
            Map<String, Object> facts,
            boolean includeSkipped,
            boolean recordTrace,
            Long userId) {

        public ExecuteArchiveRulesRequest {
            facts = facts == null ? Map.of() : facts;
        }

        public @Nullable Long objectId() {
            Object value = facts.get("context.objectId");
            return value instanceof Number number ? number.longValue() : null;
        }
    }

    public record SearchArchiveRuleTracesRequest(
            @Nullable Long schemeVersionId,
            @Nullable String triggerCode,
            @Nullable String objectTypeCode,
            @Nullable Long objectId,
            @Nullable ArchiveRuleType ruleType,
            @Nullable Integer limit,
            Long userId) {}
}
