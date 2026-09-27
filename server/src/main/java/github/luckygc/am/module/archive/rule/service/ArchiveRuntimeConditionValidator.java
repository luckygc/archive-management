package github.luckygc.am.module.archive.rule.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.metadata.ArchiveFieldDataType;
import github.luckygc.am.module.archive.rule.ArchiveRuleOperator;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeField;

import tools.jackson.databind.JsonNode;

public class ArchiveRuntimeConditionValidator {

    static final int MAX_DEPTH = 12;
    static final int MAX_NODES = 200;
    static final int MAX_SET_VALUES = 100;
    static final int MAX_TEXT_LENGTH = 2000;

    private static final Set<String> ALLOWED_OBJECT_KEYS =
            Set.of("all", "any", "not", "field", "operator", "value");
    private static final Map<ArchiveFieldDataType, Set<ArchiveRuleOperator>> OPERATORS =
            createOperatorMap();

    public ValidationResult validate(
            JsonNode condition, Map<String, ArchiveRuntimeField> fieldsByCode) {
        if (condition == null || condition.isNull()) {
            throw new BadRequestException("运行时条件不能为空");
        }
        ValidationState state = new ValidationState();
        validateNode(condition, fieldsByCode, 1, "$", state);
        return new ValidationResult(Set.copyOf(state.referencedFields), state.nodeCount);
    }

    private void validateNode(
            JsonNode node,
            Map<String, ArchiveRuntimeField> fieldsByCode,
            int depth,
            String path,
            ValidationState state) {
        if (depth > MAX_DEPTH) {
            throw invalid(path, "条件树超过最大深度 " + MAX_DEPTH);
        }
        if (++state.nodeCount > MAX_NODES) {
            throw invalid(path, "条件树节点数量超过限制 " + MAX_NODES);
        }
        if (!node.isObject()) {
            throw invalid(path, "只允许结构化条件对象，禁止脚本或 SQL");
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            if (!ALLOWED_OBJECT_KEYS.contains(entry.getKey())) {
                throw invalid(path + "." + entry.getKey(), "未知条件属性");
            }
        }
        if (node.has("all")) {
            requireOnly(node, path, "all");
            validateArray(node.get("all"), fieldsByCode, depth, path + ".all", state);
            return;
        }
        if (node.has("any")) {
            requireOnly(node, path, "any");
            validateArray(node.get("any"), fieldsByCode, depth, path + ".any", state);
            return;
        }
        if (node.has("not")) {
            requireOnly(node, path, "not");
            validateNode(node.get("not"), fieldsByCode, depth + 1, path + ".not", state);
            return;
        }
        validateFieldNode(node, fieldsByCode, path, state);
    }

    private void requireOnly(JsonNode node, String path, String key) {
        if (node.size() != 1) {
            throw invalid(path, key + " 组合节点不能混入其他属性");
        }
    }

    private void validateArray(
            JsonNode node,
            Map<String, ArchiveRuntimeField> fieldsByCode,
            int depth,
            String path,
            ValidationState state) {
        if (!node.isArray() || node.isEmpty()) {
            throw invalid(path, "组合条件不能为空");
        }
        for (int index = 0; index < node.size(); index++) {
            validateNode(node.get(index), fieldsByCode, depth + 1, path + "[" + index + "]", state);
        }
    }

    private void validateFieldNode(
            JsonNode node,
            Map<String, ArchiveRuntimeField> fieldsByCode,
            String path,
            ValidationState state) {
        String fieldCode = text(node.get("field"));
        String operatorValue = text(node.get("operator"));
        if (fieldCode == null || operatorValue == null) {
            throw invalid(path, "字段条件必须包含 field 和 operator");
        }
        ArchiveRuntimeField field = fieldsByCode.get(fieldCode);
        if (field == null || !field.readable()) {
            throw invalid(path + ".field", "字段不存在、已停用或不可读取：" + fieldCode);
        }
        ArchiveRuleOperator operator;
        try {
            operator = ArchiveRuleOperator.valueOf(operatorValue);
        } catch (IllegalArgumentException exception) {
            throw invalid(path + ".operator", "未知操作符：" + operatorValue);
        }
        if (!OPERATORS.getOrDefault(field.dataType(), Set.of()).contains(operator)) {
            throw invalid(path + ".operator", "字段类型不支持该操作符：" + fieldCode);
        }
        boolean valueRequired =
                operator != ArchiveRuleOperator.IS_EMPTY
                        && operator != ArchiveRuleOperator.IS_NOT_EMPTY;
        if (valueRequired && !node.has("value")) {
            throw invalid(path + ".value", "该操作符必须提供比较值");
        }
        if (!valueRequired && node.has("value")) {
            throw invalid(path + ".value", "空值判断不能提供比较值");
        }
        if (node.size() != (valueRequired ? 3 : 2)) {
            throw invalid(path, "字段条件包含多余属性");
        }
        if (valueRequired) {
            validateValue(node.get("value"), field.dataType(), operator, path + ".value");
        }
        state.referencedFields.add(fieldCode);
    }

    private void validateValue(
            JsonNode value,
            ArchiveFieldDataType dataType,
            ArchiveRuleOperator operator,
            String path) {
        if (operator == ArchiveRuleOperator.IN) {
            if (!value.isArray() || value.isEmpty() || value.size() > MAX_SET_VALUES) {
                throw invalid(path, "IN 参数必须为 1 至 " + MAX_SET_VALUES + " 个值");
            }
            for (int index = 0; index < value.size(); index++) {
                validateScalar(value.get(index), dataType, path + "[" + index + "]");
            }
            return;
        }
        if (operator == ArchiveRuleOperator.BETWEEN) {
            if (!value.isArray() || value.size() != 2) {
                throw invalid(path, "BETWEEN 参数必须恰好包含两个值");
            }
            validateScalar(value.get(0), dataType, path + "[0]");
            validateScalar(value.get(1), dataType, path + "[1]");
            return;
        }
        validateScalar(value, dataType, path);
    }

    private void validateScalar(JsonNode value, ArchiveFieldDataType dataType, String path) {
        if (value == null || value.isNull() || value.isArray() || value.isObject()) {
            throw invalid(path, "比较值必须是与字段类型兼容的标量");
        }
        boolean valid =
                switch (dataType) {
                    case TEXT, ENUM, ORGANIZATION, PERSON -> validText(value);
                    case INTEGER -> value.isIntegralNumber();
                    case DECIMAL, AMOUNT -> value.isNumber();
                    case BOOLEAN -> value.isBoolean();
                    case REFERENCE -> value.isIntegralNumber() || validText(value);
                    case DATE -> validDate(value);
                    case DATETIME -> validDateTime(value);
                };
        if (!valid) {
            throw invalid(path, "比较值与字段类型 " + dataType + " 不兼容");
        }
    }

    private boolean validText(JsonNode value) {
        return value.isTextual()
                && value.textValue() != null
                && value.textValue().length() <= MAX_TEXT_LENGTH;
    }

    private boolean validDate(JsonNode value) {
        if (!validText(value)) return false;
        try {
            LocalDate.parse(value.textValue());
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private boolean validDateTime(JsonNode value) {
        if (!validText(value)) return false;
        try {
            LocalDateTime.parse(value.textValue());
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private @Nullable String text(@Nullable JsonNode node) {
        return node != null && node.isTextual() ? StringUtils.trimToNull(node.textValue()) : null;
    }

    private BadRequestException invalid(String path, String message) {
        return new BadRequestException(message, "conditionJson" + path, message);
    }

    private static Map<ArchiveFieldDataType, Set<ArchiveRuleOperator>> createOperatorMap() {
        Map<ArchiveFieldDataType, Set<ArchiveRuleOperator>> operators =
                new EnumMap<>(ArchiveFieldDataType.class);
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
        Set<ArchiveRuleOperator> text =
                Set.of(
                        ArchiveRuleOperator.EQ,
                        ArchiveRuleOperator.NE,
                        ArchiveRuleOperator.CONTAINS,
                        ArchiveRuleOperator.STARTS_WITH,
                        ArchiveRuleOperator.ENDS_WITH,
                        ArchiveRuleOperator.IN,
                        ArchiveRuleOperator.IS_EMPTY,
                        ArchiveRuleOperator.IS_NOT_EMPTY);
        Set<ArchiveRuleOperator> reference =
                Set.of(
                        ArchiveRuleOperator.EQ,
                        ArchiveRuleOperator.NE,
                        ArchiveRuleOperator.IN,
                        ArchiveRuleOperator.IS_EMPTY,
                        ArchiveRuleOperator.IS_NOT_EMPTY);
        operators.put(ArchiveFieldDataType.TEXT, text);
        operators.put(ArchiveFieldDataType.INTEGER, comparable);
        operators.put(ArchiveFieldDataType.DECIMAL, comparable);
        operators.put(ArchiveFieldDataType.AMOUNT, comparable);
        operators.put(ArchiveFieldDataType.DATE, comparable);
        operators.put(ArchiveFieldDataType.DATETIME, comparable);
        operators.put(
                ArchiveFieldDataType.BOOLEAN,
                Set.of(ArchiveRuleOperator.EQ, ArchiveRuleOperator.NE));
        operators.put(ArchiveFieldDataType.ENUM, reference);
        operators.put(ArchiveFieldDataType.REFERENCE, reference);
        operators.put(ArchiveFieldDataType.ORGANIZATION, reference);
        operators.put(ArchiveFieldDataType.PERSON, reference);
        return Map.copyOf(operators);
    }

    public record ValidationResult(Set<String> referencedFields, int nodeCount) {}

    private static final class ValidationState {
        private final Set<String> referencedFields = new LinkedHashSet<>();
        private int nodeCount;
    }
}
