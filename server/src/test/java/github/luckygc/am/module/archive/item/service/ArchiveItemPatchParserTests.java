package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemPatchParser.PreparedPatch;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDetailDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("档案条目补丁候选值解析")
class ArchiveItemPatchParserTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ArchiveItemPatchParser parser = new ArchiveItemPatchParser(jsonMapper);
    private final ArchiveItemDetailDto before = detail();

    @Test
    @DisplayName("显式删除可空固定字段与省略固定字段分别保留")
    void nullableFieldDeletionPreservesUnmentionedValues() {
        PreparedPatch prepared =
                prepared("{\"item\":{\"volumeId\":null,\"securityLevelId\":null}}");

        assertThat(prepared.removedItemFields())
                .containsExactlyInAnyOrder("volumeId", "securityLevelId");
        assertThat(prepared.request().volumeId()).isNull();
        assertThat(prepared.request().securityLevelId()).isNull();
        assertThat(prepared.request().retentionPeriodId()).isEqualTo(3L);
        assertThat(prepared.request().fondsCode()).isEqualTo("F001");
        assertThat(prepared.request().archiveNo()).isEqualTo("A-001");
        assertThat(prepared.request().archiveYear()).isEqualTo(2026);
        assertThat(prepared.request().dynamicFields()).isNull();
        assertThat(prepared.request().physicalFields()).isNull();
    }

    @Test
    @DisplayName("字段域合并保留其他值并删除显式空值，原详情和补丁不被改写")
    void fieldPatchesMergeWithoutMutatingInputs() {
        var patch =
                jsonMapper.readTree(
                        "{\"dynamicFields\":{\"title\":\"新题名\",\"note\":null},"
                                + "\"physicalFieldValues\":{\"box\":null}}");
        var originalDetail = jsonMapper.valueToTree(before);
        var originalPatch = patch.deepCopy();

        PreparedPatch prepared = Objects.requireNonNull(parser.parse(before, patch));

        assertThat(prepared.request().dynamicFields())
                .containsExactlyInAnyOrderEntriesOf(Map.of("title", "新题名", "pages", 5));
        assertThat(prepared.request().physicalFields())
                .containsExactlyInAnyOrderEntriesOf(Map.of("rack", "R-01"));
        assertThat(prepared.removedItemFields()).isEmpty();
        JsonNode currentDetail = jsonMapper.valueToTree(before);
        assertThat(currentDetail).isEqualTo(originalDetail);
        assertThat(patch).isEqualTo(originalPatch);
    }

    @Test
    @DisplayName("空补丁、相同值和删除不存在的成员均不产生修改候选")
    void unchangedRepresentationProducesNoCandidate() {
        for (String patch :
                List.of(
                        "{}",
                        "{\"item\":{\"archiveNo\":\"A-001\"}}",
                        "{\"dynamicFields\":{\"title\":\"原题名\"}}",
                        "{\"dynamicFields\":{\"absent\":null}}",
                        "{\"unknown\":null}")) {
            assertThat(parser.parse(before, jsonMapper.readTree(patch))).isNull();
        }
    }

    @Test
    @DisplayName("详情中的空字段值按省略表示处理，不使空字段补丁误触发写入")
    void omittedNullValuesDoNotProduceChange() {
        Map<String, @Nullable Object> fields = new LinkedHashMap<>(before.dynamicFields());
        fields.put("empty", null);
        ArchiveItemDetailDto detail =
                new ArchiveItemDetailDto(
                        before.item(),
                        before.category(),
                        before.fields(),
                        fields,
                        before.physicalFields(),
                        before.physicalFieldValues());

        assertThat(parser.parse(detail, jsonMapper.readTree("{\"dynamicFields\":{}}"))).isNull();
    }

    @Test
    @DisplayName("可空固定字段类型错误保持原字段路径")
    void invalidOptionalFieldKeepsErrorPath() {
        assertThatThrownBy(() -> prepared("{\"item\":{\"retentionPeriodId\":\"3\"}}"))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .extracting(violation -> violation.field())
                                        .containsExactly("item.retentionPeriodId"));
    }

    private PreparedPatch prepared(String patch) {
        return Objects.requireNonNull(parser.parse(before, jsonMapper.readTree(patch)));
    }

    private ArchiveItemDetailDto detail() {
        ArchiveItemDto item =
                new ArchiveItemDto(
                        7L,
                        10L,
                        "F001",
                        "默认全宗",
                        "contract",
                        "合同档案",
                        "A-001",
                        2L,
                        3L,
                        2026,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null);
        return new ArchiveItemDetailDto(
                item,
                mock(ArchiveCategoryDto.class),
                List.of(),
                Map.of("title", "原题名", "note", "原备注", "pages", 5),
                List.of(),
                Map.of("box", "B-01", "rack", "R-01"));
    }
}
