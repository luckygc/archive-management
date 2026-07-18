package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.metadata.ArchiveFieldDataType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeFieldSource;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeField;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("运行时结构化条件校验")
class ArchiveRuntimeConditionValidatorTests {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private final ArchiveRuntimeConditionValidator validator =
            new ArchiveRuntimeConditionValidator();

    @Test
    @DisplayName("接受类型兼容的 all any not 条件并返回引用字段")
    void acceptsTypedStructuredAst() throws Exception {
        var result =
                validator.validate(
                        JSON_MAPPER.readTree(
                                """
                                {"all":[
                                  {"field":"item.archiveYear","operator":"GTE","value":2020},
                                  {"not":{"field":"metadata.title","operator":"IS_EMPTY"}},
                                  {"any":[
                                    {"field":"metadata.title","operator":"STARTS_WITH","value":"A"},
                                    {"field":"metadata.title","operator":"EQ","value":"B"}
                                  ]}
                                ]}
                                """),
                        fields());

        assertThat(result.referencedFields())
                .containsExactlyInAnyOrder("item.archiveYear", "metadata.title");
    }

    @Test
    @DisplayName("拒绝脚本未知字段和类型不兼容操作符")
    void rejectsScriptsUnknownFieldsAndInvalidOperatorTypes() throws Exception {
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        JSON_MAPPER.readTree("{\"script\":\"drop table x\"}"),
                                        fields()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        JSON_MAPPER.readTree(
                                                "{\"field\":\"metadata.missing\",\"operator\":\"EQ\",\"value\":1}"),
                                        fields()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        JSON_MAPPER.readTree(
                                                "{\"field\":\"item.archiveYear\",\"operator\":\"CONTAINS\",\"value\":\"2\"}"),
                                        fields()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("拒绝过深条件和超限集合")
    void rejectsResourceLimitOverflow() throws Exception {
        String nested = "{\"field\":\"metadata.title\",\"operator\":\"EQ\",\"value\":\"A\"}";
        for (int index = 0; index < ArchiveRuntimeConditionValidator.MAX_DEPTH; index++) {
            nested = "{\"not\":" + nested + "}";
        }
        String tooDeep = nested;
        assertThatThrownBy(() -> validator.validate(JSON_MAPPER.readTree(tooDeep), fields()))
                .isInstanceOf(BadRequestException.class);

        String values =
                java.util.stream.IntStream.rangeClosed(
                                1, ArchiveRuntimeConditionValidator.MAX_SET_VALUES + 1)
                        .mapToObj(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","));
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        JSON_MAPPER.readTree(
                                                "{\"field\":\"item.archiveYear\",\"operator\":\"IN\",\"value\":["
                                                        + values
                                                        + "]}"),
                                        fields()))
                .isInstanceOf(BadRequestException.class);
    }

    private Map<String, ArchiveRuntimeField> fields() {
        return Map.of(
                "item.archiveYear",
                field("item.archiveYear", ArchiveFieldDataType.INTEGER),
                "metadata.title",
                field("metadata.title", ArchiveFieldDataType.TEXT));
    }

    private ArchiveRuntimeField field(String code, ArchiveFieldDataType type) {
        return new ArchiveRuntimeField(
                code, code, type, ArchiveRuntimeFieldSource.FIXED, true, true, null);
    }
}
