package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("运行时结构化条件求值")
class ArchiveRuntimeConditionEvaluatorTests {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private final ArchiveRuntimeConditionEvaluator evaluator =
            new ArchiveRuntimeConditionEvaluator();

    @Test
    @DisplayName("按类型执行组合比较和空值判断")
    void evaluatesStructuredCondition() throws Exception {
        var condition =
                JSON_MAPPER.readTree(
                        """
                        {"all":[
                          {"field":"item.archiveYear","operator":"BETWEEN","value":[2020,2030]},
                          {"field":"metadata.title","operator":"CONTAINS","value":"档案"},
                          {"not":{"field":"metadata.title","operator":"IS_EMPTY"}}
                        ]}
                        """);

        assertThat(
                        evaluator.evaluate(
                                condition,
                                Map.of("item.archiveYear", 2026, "metadata.title", "电子档案")))
                .isTrue();
        assertThat(
                        evaluator.evaluate(
                                condition,
                                Map.of("item.archiveYear", 2031, "metadata.title", "电子档案")))
                .isFalse();
    }
}
