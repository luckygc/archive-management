package github.luckygc.am.module.archive.rule.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.mapper.ArchiveRuntimeExecutionCriteria;
import github.luckygc.am.module.archive.rule.ArchiveRuleDecisionSeverity;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeActionDataRepository;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeActionExecutionContext.FieldAssignment;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ArchiveRuntimeExecutionService {

    private static final int MAX_DEFINITIONS_PER_EXECUTION = 200;
    private static final int MAX_ACTIONS_PER_EXECUTION = 500;

    private final ArchiveRuleMapper ruleMapper;
    private final ArchiveRuntimeActionDataRepository actionRepository;
    private final ArchiveRuntimeFieldCatalogService fieldCatalogService;
    private final Map<ArchiveRuntimeActionType, ArchiveRuntimeActionHandler> actionHandlers;
    private final ArchiveRuntimeConditionValidator conditionValidator =
            new ArchiveRuntimeConditionValidator();
    private final ArchiveRuntimeConditionEvaluator conditionEvaluator =
            new ArchiveRuntimeConditionEvaluator();
    private final JsonMapper jsonMapper;

    public ArchiveRuntimeExecutionService(
            ArchiveRuleMapper ruleMapper,
            ArchiveRuntimeActionDataRepository actionRepository,
            ArchiveRuntimeFieldCatalogService fieldCatalogService,
            List<ArchiveRuntimeActionHandler> handlers,
            JsonMapper jsonMapper) {
        this.ruleMapper = ruleMapper;
        this.actionRepository = actionRepository;
        this.fieldCatalogService = fieldCatalogService;
        this.actionHandlers = indexHandlers(handlers);
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public ArchiveRuntimeExecutionResult simulate(ArchiveRuntimeExecutionRequest request) {
        return evaluate(request);
    }

    @Transactional(readOnly = true)
    public ArchiveRuntimeExecutionResult enforce(ArchiveRuntimeExecutionRequest request) {
        ArchiveRuntimeExecutionResult result = evaluate(request);
        if (result.blocking()) throw new ArchiveRuntimeBlockedException(result);
        return result;
    }

    private ArchiveRuntimeExecutionResult evaluate(ArchiveRuntimeExecutionRequest request) {
        if (request.triggerPoint() == null) {
            throw new BadRequestException("运行时触发点不能为空");
        }
        ArchiveRuntimeFieldCatalog catalog =
                fieldCatalogService.catalog(
                        request.schemeVersionId(), request.categoryCode(), request.triggerPoint());
        List<ArchiveRuntimeDefinition> definitions =
                ruleMapper.listExecutableRuntimeDefinitions(
                        new ArchiveRuntimeExecutionCriteria(
                                request.schemeVersionId(),
                                request.triggerPoint(),
                                StringUtils.trimToNull(request.fondsCode()),
                                StringUtils.trimToNull(request.categoryCode()),
                                request.archiveLevel() == null
                                        ? request.triggerPoint().archiveLevel().name()
                                        : request.archiveLevel().name()));
        if (definitions.size() > MAX_DEFINITIONS_PER_EXECUTION) {
            throw new BadRequestException("运行时定义数量超过单次执行上限");
        }

        Map<String, @Nullable Object> candidateFacts =
                ArchiveRuntimeActionExecutionContext.mutableFacts(request.candidateFacts());
        Map<String, FieldAssignment> assignments = new LinkedHashMap<>();
        List<ArchiveRuntimeDecision> decisions = new ArrayList<>();
        ExecutionCounter counter = new ExecutionCounter();

        for (ArchiveRuntimeDefinition definition : definitions) {
            if (definition.getDefinitionKind() == ArchiveRuntimeDefinitionKind.RULE) {
                decisions.add(
                        executeRule(definition, catalog, candidateFacts, assignments, counter));
            }
        }
        for (ArchiveRuntimeDefinition definition : definitions) {
            if (definition.getDefinitionKind() == ArchiveRuntimeDefinitionKind.CONSTRAINT) {
                decisions.add(executeConstraint(definition, catalog, candidateFacts));
            }
        }
        boolean blocking = decisions.stream().anyMatch(ArchiveRuntimeDecision::blocking);
        List<ArchiveRuntimeWarning> warnings =
                decisions.stream()
                        .filter(
                                decision ->
                                        decision.severity() == ArchiveRuleDecisionSeverity.WARNING)
                        .map(
                                decision ->
                                        new ArchiveRuntimeWarning(
                                                decision.definitionCode(), decision.message()))
                        .toList();
        return new ArchiveRuntimeExecutionResult(
                Collections.unmodifiableMap(new LinkedHashMap<>(candidateFacts)),
                Map.copyOf(assignments),
                List.copyOf(decisions),
                warnings,
                blocking);
    }

    private ArchiveRuntimeDecision executeRule(
            ArchiveRuntimeDefinition definition,
            ArchiveRuntimeFieldCatalog catalog,
            Map<String, @Nullable Object> candidateFacts,
            Map<String, FieldAssignment> assignments,
            ExecutionCounter counter) {
        JsonNode condition = validatedCondition(definition, catalog);
        boolean matched = conditionEvaluator.evaluate(condition, candidateFacts);
        if (!matched) {
            return new ArchiveRuntimeDecision(
                    definition.getId(),
                    definition.getDefinitionCode(),
                    definition.getDefinitionKind(),
                    false,
                    List.of(),
                    null,
                    ArchiveRuleDecisionSeverity.INFO,
                    false,
                    "规则条件未命中");
        }
        List<ArchiveRuntimeActionDecision> actionDecisions = new ArrayList<>();
        boolean blocking = false;
        ArchiveRuleDecisionSeverity severity = ArchiveRuleDecisionSeverity.INFO;
        @Nullable String message = null;
        for (ArchiveRuntimeAction action :
                actionRepository.findByDefinitionId(definition.getId())) {
            if (++counter.actions > MAX_ACTIONS_PER_EXECUTION) {
                throw new BadRequestException("运行时动作数量超过单次执行上限");
            }
            ArchiveRuntimeActionHandler handler = requireHandler(action.getActionType());
            handler.validate(action, definition.getTriggerPoint(), catalog);
            ArchiveRuntimeActionDecision decision =
                    handler.execute(
                            action,
                            new ArchiveRuntimeActionExecutionContext(
                                    definition.getDefinitionCode(),
                                    catalog,
                                    candidateFacts,
                                    assignments));
            actionDecisions.add(decision);
            if (action.getActionType() == ArchiveRuntimeActionType.REJECT) {
                blocking = true;
                severity = ArchiveRuleDecisionSeverity.ERROR;
            } else if (action.getActionType() == ArchiveRuntimeActionType.WARN
                    && severity != ArchiveRuleDecisionSeverity.ERROR) {
                severity = ArchiveRuleDecisionSeverity.WARNING;
            }
            if (message == null) message = message(action);
        }
        return new ArchiveRuntimeDecision(
                definition.getId(),
                definition.getDefinitionCode(),
                definition.getDefinitionKind(),
                true,
                List.copyOf(actionDecisions),
                message,
                severity,
                blocking,
                null);
    }

    private ArchiveRuntimeDecision executeConstraint(
            ArchiveRuntimeDefinition definition,
            ArchiveRuntimeFieldCatalog catalog,
            Map<String, @Nullable Object> candidateFacts) {
        JsonNode condition = validatedCondition(definition, catalog);
        boolean assertionSatisfied = conditionEvaluator.evaluate(condition, candidateFacts);
        if (assertionSatisfied) {
            return new ArchiveRuntimeDecision(
                    definition.getId(),
                    definition.getDefinitionCode(),
                    definition.getDefinitionKind(),
                    true,
                    List.of(),
                    null,
                    ArchiveRuleDecisionSeverity.INFO,
                    false,
                    null);
        }
        ArchiveRuntimeActionType actionType = definition.getConstraintAction();
        if (actionType != ArchiveRuntimeActionType.REJECT
                && actionType != ArchiveRuntimeActionType.WARN) {
            throw new BadRequestException("运行时约束失败处理失效：" + definition.getDefinitionCode());
        }
        String message = StringUtils.trimToNull(definition.getConstraintMessage());
        if (message == null) {
            throw new BadRequestException("运行时约束失败消息失效：" + definition.getDefinitionCode());
        }
        return new ArchiveRuntimeDecision(
                definition.getId(),
                definition.getDefinitionCode(),
                definition.getDefinitionKind(),
                false,
                List.of(new ArchiveRuntimeActionDecision(actionType, Map.of("message", message))),
                message,
                actionType == ArchiveRuntimeActionType.REJECT
                        ? ArchiveRuleDecisionSeverity.ERROR
                        : ArchiveRuleDecisionSeverity.WARNING,
                actionType == ArchiveRuntimeActionType.REJECT,
                "约束断言未满足");
    }

    private JsonNode validatedCondition(
            ArchiveRuntimeDefinition definition, ArchiveRuntimeFieldCatalog catalog) {
        JsonNode condition = toConditionNode(definition.getConditionJson());
        conditionValidator.validate(condition, catalog.fieldsByCode());
        if (definition.getScopeCategoryCode() != null
                && !catalog.signature().equals(definition.getFieldCatalogSignature())) {
            throw new BadRequestException("已发布运行时定义字段目录已失效：" + definition.getDefinitionCode());
        }
        return condition;
    }

    private ArchiveRuntimeActionHandler requireHandler(ArchiveRuntimeActionType actionType) {
        ArchiveRuntimeActionHandler handler = actionHandlers.get(actionType);
        if (handler == null) {
            throw new BadRequestException("系统未注册固定动作：" + actionType);
        }
        return handler;
    }

    private @Nullable String message(ArchiveRuntimeAction action) {
        Object value = action.getActionParams().get("message");
        return value instanceof String text ? StringUtils.trimToNull(text) : null;
    }

    private JsonNode toConditionNode(Map<String, Object> condition) {
        try {
            return jsonMapper.readTree(jsonMapper.writeValueAsString(condition));
        } catch (Exception exception) {
            throw new BadRequestException("已发布运行时条件不是有效 JSON 结构");
        }
    }

    private Map<ArchiveRuntimeActionType, ArchiveRuntimeActionHandler> indexHandlers(
            List<ArchiveRuntimeActionHandler> handlers) {
        Map<ArchiveRuntimeActionType, ArchiveRuntimeActionHandler> indexed =
                new EnumMap<>(ArchiveRuntimeActionType.class);
        handlers.forEach(handler -> indexed.put(handler.actionType(), handler));
        return Map.copyOf(indexed);
    }

    public record ArchiveRuntimeExecutionRequest(
            Long schemeVersionId,
            ArchiveRuntimeTriggerPoint triggerPoint,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            @Nullable ArchiveLevel archiveLevel,
            @Nullable String objectTypeCode,
            @Nullable Long objectId,
            Map<String, @Nullable Object> candidateFacts,
            Long userId) {

        public ArchiveRuntimeExecutionRequest {
            candidateFacts = candidateFacts == null ? Map.of() : candidateFacts;
        }
    }

    public record ArchiveRuntimeExecutionResult(
            Map<String, @Nullable Object> candidateFacts,
            Map<String, FieldAssignment> assignments,
            List<ArchiveRuntimeDecision> decisions,
            List<ArchiveRuntimeWarning> warnings,
            boolean blocking) {}

    public record ArchiveRuntimeWarning(String definitionCode, @Nullable String message) {}

    private static final class ExecutionCounter {
        private int actions;
    }
}
