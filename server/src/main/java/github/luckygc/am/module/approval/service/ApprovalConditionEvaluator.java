package github.luckygc.am.module.approval.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import github.luckygc.am.module.approval.ApprovalConditionOperator;

@Component("approvalConditionEvaluator")
public class ApprovalConditionEvaluator {

    public boolean matches(
            Map<?, ?> businessContext, String field, String operator, String encodedValues) {
        Object raw = businessContext == null ? null : businessContext.get(field);
        String actual = raw == null ? null : raw.toString();
        List<String> expected = decodeValues(encodedValues);
        return switch (ApprovalConditionOperator.valueOf(operator)) {
            case EQUALS -> actual != null && actual.equals(expected.getFirst());
            case NOT_EQUALS -> actual == null || !actual.equals(expected.getFirst());
            case IN -> actual != null && expected.contains(actual);
        };
    }

    private List<String> decodeValues(String encodedValues) {
        return Arrays.stream(encodedValues.split("\\.", -1))
                .map(
                        value ->
                                new String(
                                        Base64.getUrlDecoder().decode(value),
                                        StandardCharsets.UTF_8))
                .toList();
    }
}
