package github.luckygc.am.module.archive.rule.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.rule.ArchiveRuleOperator;

import tools.jackson.databind.JsonNode;

public class ArchiveRuleConditionEvaluator {

    public boolean evaluate(JsonNode condition, Map<String, Object> facts) {
        if (condition.has("all")) {
            for (JsonNode child : condition.get("all")) {
                if (!evaluate(child, facts)) {
                    return false;
                }
            }
            return true;
        }
        if (condition.has("any")) {
            for (JsonNode child : condition.get("any")) {
                if (evaluate(child, facts)) {
                    return true;
                }
            }
            return false;
        }
        if (condition.has("not")) {
            return !evaluate(condition.get("not"), facts);
        }
        String field = condition.get("field").textValue();
        ArchiveRuleOperator operator =
                ArchiveRuleOperator.valueOf(condition.get("operator").textValue());
        Object actualValue = facts.get(field);
        JsonNode expectedNode = condition.get("value");
        return evaluateField(actualValue, operator, expectedNode);
    }

    private boolean evaluateField(
            @Nullable Object actualValue,
            ArchiveRuleOperator operator,
            @Nullable JsonNode expectedNode) {
        return switch (operator) {
            case EQ -> compare(actualValue, expectedNode) == 0;
            case NE -> compare(actualValue, expectedNode) != 0;
            case CONTAINS -> string(actualValue).contains(string(expectedNode));
            case STARTS_WITH -> string(actualValue).startsWith(string(expectedNode));
            case ENDS_WITH -> string(actualValue).endsWith(string(expectedNode));
            case GT -> compare(actualValue, expectedNode) > 0;
            case GTE -> compare(actualValue, expectedNode) >= 0;
            case LT -> compare(actualValue, expectedNode) < 0;
            case LTE -> compare(actualValue, expectedNode) <= 0;
            case BETWEEN -> between(actualValue, expectedNode);
            case IN -> in(actualValue, expectedNode);
            case IS_EMPTY -> isEmpty(actualValue);
            case IS_NOT_EMPTY -> !isEmpty(actualValue);
        };
    }

    private int compare(@Nullable Object actualValue, @Nullable JsonNode expectedNode) {
        if (actualValue == null && (expectedNode == null || expectedNode.isNull())) {
            return 0;
        }
        if (actualValue == null) {
            return -1;
        }
        if (expectedNode == null || expectedNode.isNull()) {
            return 1;
        }
        BigDecimal actualNumber = number(actualValue);
        BigDecimal expectedNumber = number(expectedNode);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber);
        }
        return String.valueOf(actualValue).compareTo(string(expectedNode));
    }

    private boolean between(@Nullable Object actualValue, @Nullable JsonNode expectedNode) {
        if (expectedNode == null || !expectedNode.isArray() || expectedNode.size() != 2) {
            return false;
        }
        return compare(actualValue, expectedNode.get(0)) >= 0
                && compare(actualValue, expectedNode.get(1)) <= 0;
    }

    private boolean in(@Nullable Object actualValue, @Nullable JsonNode expectedNode) {
        if (expectedNode == null || !expectedNode.isArray()) {
            return false;
        }
        for (JsonNode value : expectedNode) {
            if (compare(actualValue, value) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isEmpty(@Nullable Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return StringUtils.isBlank(text);
        }
        if (value instanceof Iterable<?> iterable) {
            return !iterable.iterator().hasNext();
        }
        return false;
    }

    private String string(@Nullable Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String string(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? Objects.toString(node.textValue(), "") : node.toString();
    }

    private @Nullable BigDecimal number(@Nullable Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof CharSequence text && StringUtils.isNotBlank(text)) {
            try {
                return new BigDecimal(text.toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private @Nullable BigDecimal number(JsonNode node) {
        if (!node.isNumber()) {
            return null;
        }
        return new BigDecimal(node.numberValue().toString());
    }
}
