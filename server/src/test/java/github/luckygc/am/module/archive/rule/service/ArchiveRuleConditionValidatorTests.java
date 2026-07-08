package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeDataType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("档案本地规则条件树校验")
class ArchiveRuleConditionValidatorTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("允许 all 组合字段条件")
    void validateShouldAcceptAllFieldConditions() throws Exception {
        JsonNode condition =
                jsonMapper.readTree(
                        """
                        {
                          "all": [
                            {"field": "item.documentNo", "operator": "CONTAINS", "value": "办"},
                            {"field": "item.archiveYear", "operator": "GTE", "value": 2026}
                          ]
                        }
                        """);
        ArchiveRuleConditionValidator validator =
                new ArchiveRuleConditionValidator(
                        field ->
                                switch (field) {
                                    case "item.documentNo" -> ArchiveOntologyAttributeDataType.TEXT;
                                    case "item.archiveYear" ->
                                            ArchiveOntologyAttributeDataType.INTEGER;
                                    default -> null;
                                });

        assertThatNoException().isThrownBy(() -> validator.validate(condition));
    }

    @Test
    @DisplayName("拒绝自由表达式条件")
    void validateShouldRejectFreeExpression() throws Exception {
        JsonNode condition =
                jsonMapper.readTree(
                        """
                        {"expression": "1 = 1"}
                        """);
        ArchiveRuleConditionValidator validator =
                new ArchiveRuleConditionValidator(field -> ArchiveOntologyAttributeDataType.TEXT);

        assertThatThrownBy(() -> validator.validate(condition))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("本地规则只支持结构化条件树");
    }

    @Test
    @DisplayName("拒绝字段类型不支持的操作符")
    void validateShouldRejectUnsupportedOperator() throws Exception {
        JsonNode condition =
                jsonMapper.readTree(
                        """
                        {"field": "item.archiveYear", "operator": "CONTAINS", "value": "20"}
                        """);
        ArchiveRuleConditionValidator validator =
                new ArchiveRuleConditionValidator(
                        field -> ArchiveOntologyAttributeDataType.INTEGER);

        assertThatThrownBy(() -> validator.validate(condition))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("字段类型不支持该操作符");
    }
}
