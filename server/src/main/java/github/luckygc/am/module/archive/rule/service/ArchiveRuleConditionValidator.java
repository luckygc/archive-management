package github.luckygc.am.module.archive.rule.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeDataType;
import github.luckygc.am.module.archive.rule.ArchiveRuleOperator;

import tools.jackson.databind.JsonNode;

public class ArchiveRuleConditionValidator {

    private static final int MAX_DEPTH = 12;
    private static final int MAX_NODES = 200;
    private static final Set<String> ALLOWED_OBJECT_KEYS =
            Set.of("all", "any", "not", "field", "operator", "value");
    private static final Map<ArchiveOntologyAttributeDataType, Set<ArchiveRuleOperator>> OPERATORS =
            createOperatorMap();

    private final ArchiveRuleFieldResolver fieldResolver;

    public ArchiveRuleConditionValidator(ArchiveRuleFieldResolver fieldResolver) {
        this.fieldResolver = fieldResolver;
    }

    public void validate(JsonNode condition) {
        if (condition == null || condition.isNull()) {
            throw new BadRequestException("规则条件不能为空");
        }
        validateNode(condition, 1, new Counter());
    }

    private void validateNode(JsonNode node, int depth, Counter counter) {
        if (depth > MAX_DEPTH) {
            throw new BadRequestException("规则条件树超过最大深度");
        }
        if (++counter.value > MAX_NODES) {
            throw new BadRequestException("规则条件树节点数量超过限制");
        }
        if (!node.isObject()) {
            throw new BadRequestException("本地规则只支持结构化条件树");
        }
        rejectUnknownKeys(node);
        if (node.has("all")) {
            validateArrayNode(node.get("all"), depth, counter);
            return;
        }
        if (node.has("any")) {
            validateArrayNode(node.get("any"), depth, counter);
            return;
        }
        if (node.has("not")) {
            validateNode(node.get("not"), depth + 1, counter);
            return;
        }
        validateFieldNode(node);
    }

    private void rejectUnknownKeys(JsonNode node) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String name = entry.getKey();
            if (!ALLOWED_OBJECT_KEYS.contains(name)) {
                throw new BadRequestException("本地规则只支持结构化条件树");
            }
        }
    }

    private void validateArrayNode(JsonNode arrayNode, int depth, Counter counter) {
        if (!arrayNode.isArray() || arrayNode.isEmpty()) {
            throw new BadRequestException("规则组合条件不能为空");
        }
        for (JsonNode child : arrayNode) {
            validateNode(child, depth + 1, counter);
        }
    }

    private void validateFieldNode(JsonNode node) {
        String field = textValue(node.get("field"));
        String operatorValue = textValue(node.get("operator"));
        if (field == null || operatorValue == null) {
            throw new BadRequestException("字段条件必须包含字段引用和操作符");
        }
        ArchiveOntologyAttributeDataType dataType = fieldResolver.resolve(field);
        if (dataType == null) {
            throw new BadRequestException("规则字段无法解析");
        }
        ArchiveRuleOperator operator;
        try {
            operator = ArchiveRuleOperator.valueOf(operatorValue);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("未知规则操作符");
        }
        if (!OPERATORS.getOrDefault(dataType, Set.of()).contains(operator)) {
            throw new BadRequestException("字段类型不支持该操作符");
        }
    }

    private @Nullable String textValue(@Nullable JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        return StringUtils.trimToNull(node.textValue());
    }

    private static Map<ArchiveOntologyAttributeDataType, Set<ArchiveRuleOperator>>
            createOperatorMap() {
        Map<ArchiveOntologyAttributeDataType, Set<ArchiveRuleOperator>> operators =
                new EnumMap<>(ArchiveOntologyAttributeDataType.class);
        Set<ArchiveRuleOperator> comparable =
                Set.of(
                        ArchiveRuleOperator.EQ,
                        ArchiveRuleOperator.NE,
                        ArchiveRuleOperator.GT,
                        ArchiveRuleOperator.GTE,
                        ArchiveRuleOperator.LT,
                        ArchiveRuleOperator.LTE,
                        ArchiveRuleOperator.BETWEEN,
                        ArchiveRuleOperator.IN,
                        ArchiveRuleOperator.IS_EMPTY,
                        ArchiveRuleOperator.IS_NOT_EMPTY);
        operators.put(
                ArchiveOntologyAttributeDataType.TEXT,
                Set.of(
                        ArchiveRuleOperator.EQ,
                        ArchiveRuleOperator.NE,
                        ArchiveRuleOperator.CONTAINS,
                        ArchiveRuleOperator.STARTS_WITH,
                        ArchiveRuleOperator.ENDS_WITH,
                        ArchiveRuleOperator.IN,
                        ArchiveRuleOperator.IS_EMPTY,
                        ArchiveRuleOperator.IS_NOT_EMPTY));
        operators.put(ArchiveOntologyAttributeDataType.INTEGER, comparable);
        operators.put(ArchiveOntologyAttributeDataType.DECIMAL, comparable);
        operators.put(ArchiveOntologyAttributeDataType.AMOUNT, comparable);
        operators.put(ArchiveOntologyAttributeDataType.DATE, comparable);
        operators.put(ArchiveOntologyAttributeDataType.DATETIME, comparable);
        operators.put(
                ArchiveOntologyAttributeDataType.BOOLEAN,
                Set.of(ArchiveRuleOperator.EQ, ArchiveRuleOperator.NE));
        operators.put(
                ArchiveOntologyAttributeDataType.ENUM,
                Set.of(
                        ArchiveRuleOperator.EQ,
                        ArchiveRuleOperator.NE,
                        ArchiveRuleOperator.IN,
                        ArchiveRuleOperator.IS_EMPTY,
                        ArchiveRuleOperator.IS_NOT_EMPTY));
        operators.put(
                ArchiveOntologyAttributeDataType.REFERENCE,
                Set.of(
                        ArchiveRuleOperator.EQ,
                        ArchiveRuleOperator.NE,
                        ArchiveRuleOperator.IN,
                        ArchiveRuleOperator.IS_EMPTY,
                        ArchiveRuleOperator.IS_NOT_EMPTY));
        operators.put(
                ArchiveOntologyAttributeDataType.ORGANIZATION,
                operators.get(ArchiveOntologyAttributeDataType.REFERENCE));
        operators.put(
                ArchiveOntologyAttributeDataType.PERSON,
                operators.get(ArchiveOntologyAttributeDataType.REFERENCE));
        return operators;
    }

    @FunctionalInterface
    public interface ArchiveRuleFieldResolver {
        @Nullable ArchiveOntologyAttributeDataType resolve(String field);
    }

    private static final class Counter {
        private int value;
    }
}
