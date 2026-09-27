package github.luckygc.am.module.archive.rule.service;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.rule.ArchiveRuleOperator;

import tools.jackson.databind.JsonNode;

public class ArchiveRuntimeConditionEvaluator {

    public boolean evaluate(JsonNode condition, Map<String, @Nullable Object> facts) {
        if (condition.has("all")) {
            for (JsonNode child : condition.get("all")) {
                if (!evaluate(child, facts)) return false;
            }
            return true;
        }
        if (condition.has("any")) {
            for (JsonNode child : condition.get("any")) {
                if (evaluate(child, facts)) return true;
            }
            return false;
        }
        if (condition.has("not")) {
            return !evaluate(condition.get("not"), facts);
        }
        String field = condition.get("field").textValue();
        ArchiveRuleOperator operator =
                ArchiveRuleOperator.valueOf(condition.get("operator").textValue());
        return evaluateField(facts.get(field), operator, condition.get("value"));
    }

    private boolean evaluateField(
            @Nullable Object actual, ArchiveRuleOperator operator, @Nullable JsonNode expected) {
        return switch (operator) {
            case EQ -> compare(actual, expected) == 0;
            case NE -> compare(actual, expected) != 0;
            case CONTAINS -> string(actual).contains(string(expected));
            case STARTS_WITH -> string(actual).startsWith(string(expected));
            case ENDS_WITH -> string(actual).endsWith(string(expected));
            case GT -> compare(actual, expected) > 0;
            case GTE -> compare(actual, expected) >= 0;
            case LT -> compare(actual, expected) < 0;
            case LTE -> compare(actual, expected) <= 0;
            case BETWEEN -> between(actual, expected);
            case IN -> in(actual, expected);
            case IS_EMPTY -> isEmpty(actual);
            case IS_NOT_EMPTY -> !isEmpty(actual);
        };
    }

    private int compare(@Nullable Object actual, @Nullable JsonNode expected) {
        if (actual == null && (expected == null || expected.isNull())) return 0;
        if (actual == null) return -1;
        if (expected == null || expected.isNull()) return 1;
        BigDecimal actualNumber = number(actual);
        BigDecimal expectedNumber = number(expected);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber);
        }
        if (actual instanceof Boolean bool && expected.isBoolean()) {
            return Boolean.compare(bool, expected.booleanValue());
        }
        return string(actual).compareTo(string(expected));
    }

    private boolean between(@Nullable Object actual, @Nullable JsonNode expected) {
        return expected != null
                && expected.isArray()
                && expected.size() == 2
                && compare(actual, expected.get(0)) >= 0
                && compare(actual, expected.get(1)) <= 0;
    }

    private boolean in(@Nullable Object actual, @Nullable JsonNode expected) {
        if (expected == null || !expected.isArray()) return false;
        for (JsonNode value : expected) {
            if (compare(actual, value) == 0) return true;
        }
        return false;
    }

    private boolean isEmpty(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof CharSequence text) return StringUtils.isBlank(text);
        if (value instanceof Iterable<?> iterable) return !iterable.iterator().hasNext();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return false;
    }

    private String string(@Nullable Object value) {
        if (value == null) return "";
        if (value instanceof TemporalAccessor) return value.toString();
        return String.valueOf(value);
    }

    private String string(@Nullable JsonNode node) {
        if (node == null || node.isNull()) return "";
        return node.isTextual() ? Objects.toString(node.textValue(), "") : node.toString();
    }

    private @Nullable BigDecimal number(@Nullable Object value) {
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return null;
    }

    private @Nullable BigDecimal number(JsonNode node) {
        return node.isNumber() ? new BigDecimal(node.numberValue().toString()) : null;
    }
}
