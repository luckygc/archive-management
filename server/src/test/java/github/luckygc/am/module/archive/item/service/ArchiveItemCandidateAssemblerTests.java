package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.item.service.ArchiveItemCandidateAssembler.ItemCandidate;
import github.luckygc.am.module.archive.item.service.ArchiveItemCandidateAssembler.ItemPolicyCommand;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;

@DisplayName("档案条目运行时候选值组装")
class ArchiveItemCandidateAssemblerTests {

    private final ArchiveItemCandidateAssembler assembler =
            new ArchiveItemCandidateAssembler(new ArchiveItemFieldValueConverter());

    @Test
    @DisplayName("规则输入包含声明字段及空值，元数据和实物字段各自保留命名空间")
    void executionRequestPreservesContextAndDeclaredFieldScope() {
        ArchiveCategoryDto category = mock(ArchiveCategoryDto.class);
        when(category.categoryCode()).thenReturn("contract");
        when(category.categoryName()).thenReturn("合同档案");
        ArchiveFieldDto title = field("title", ArchiveFieldType.TEXT);
        ArchiveFieldDto missing = field("missing", ArchiveFieldType.TEXT);
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 9, 30);

        var request =
                assembler.executionRequest(
                        new ItemPolicyCommand(
                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE,
                                7L,
                                "F001",
                                "默认全宗",
                                category,
                                new ItemCandidate(
                                        "A-001",
                                        2026,
                                        null,
                                        3L,
                                        Map.of("title", "元数据题名", "undeclared", "不进入规则"),
                                        Map.of("title", "实物题名")),
                                List.of(title, missing),
                                List.of(title),
                                9L),
                        now);

        assertThat(request.objectId()).isEqualTo(7L);
        assertThat(request.archiveLevel()).isEqualTo(ArchiveLevel.ITEM);
        assertThat(request.objectTypeCode()).isEqualTo("ARCHIVE_ITEM");
        assertThat(request.fondsCode()).isEqualTo("F001");
        assertThat(request.categoryCode()).isEqualTo("contract");
        assertThat(request.userId()).isEqualTo(9L);
        assertThat(request.candidateFacts())
                .containsEntry("item.securityLevelId", null)
                .containsEntry("metadata.missing", null)
                .containsEntry("metadata.title", "元数据题名")
                .containsEntry("physical.title", "实物题名")
                .containsEntry("context.now", now)
                .containsEntry("context.operation", "ITEM_BEFORE_UPDATE")
                .doesNotContainKey("metadata.undeclared");
    }

    @Test
    @DisplayName("规则输出重新转换固定及动态字段并保留可空值")
    void finalCandidateConvertsRuntimeAssignments() {
        Map<String, @Nullable Object> facts = new LinkedHashMap<>();
        facts.put("item.archiveNo", " A-002 ");
        facts.put("item.archiveYear", "2026");
        facts.put("item.securityLevelId", "2");
        facts.put("item.retentionPeriodId", null);
        facts.put("metadata.count", "12");
        facts.put("physical.box", " B-01 ");

        var candidate =
                assembler.finalCandidate(
                        result(facts),
                        List.of(
                                field("count", ArchiveFieldType.INTEGER),
                                field("empty", ArchiveFieldType.TEXT)),
                        List.of(field("box", ArchiveFieldType.TEXT)));

        assertThat(candidate.archiveNo()).isEqualTo("A-002");
        assertThat(candidate.archiveYear()).isEqualTo(2026);
        assertThat(candidate.securityLevelId()).isEqualTo(2L);
        assertThat(candidate.retentionPeriodId()).isNull();
        assertThat(candidate.dynamicFields())
                .containsEntry("count", 12)
                .containsEntry("empty", null);
        assertThat(candidate.physicalFields())
                .containsExactlyInAnyOrderEntriesOf(Map.of("box", "B-01"));
        assertThat(facts)
                .containsEntry("metadata.count", "12")
                .containsEntry("physical.box", " B-01 ");
    }

    @Test
    @DisplayName("规则产生不合法实物值时仍返回实物字段错误路径")
    void invalidRuntimePhysicalValueKeepsFieldPath() {
        assertThatThrownBy(
                        () ->
                                assembler.finalCandidate(
                                        result(
                                                Map.of(
                                                        "physical.copies",
                                                        "不是整数",
                                                        "item.archiveYear",
                                                        2026)),
                                        List.of(),
                                        List.of(field("copies", ArchiveFieldType.INTEGER))))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .extracting(violation -> violation.field())
                                        .containsExactly("physicalFields.copies"));
    }

    @Test
    @DisplayName("规则产生不合法固定值时仍按原异常拒绝候选")
    void invalidRuntimeFixedValueIsRejected() {
        for (String field :
                List.of("item.archiveYear", "item.securityLevelId", "item.retentionPeriodId")) {
            Map<String, @Nullable Object> facts = new LinkedHashMap<>();
            facts.put("item.archiveYear", 2026);
            facts.put(field, "不是整数");

            assertThatThrownBy(() -> assembler.finalCandidate(result(facts), List.of(), List.of()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("运行时规则字段值类型不兼容：" + field);
        }
    }

    private ArchiveRuntimeExecutionResult result(Map<String, @Nullable Object> facts) {
        return new ArchiveRuntimeExecutionResult(facts, Map.of(), List.of(), List.of(), false);
    }

    private ArchiveFieldDto field(String code, ArchiveFieldType type) {
        ArchiveFieldDto field = mock(ArchiveFieldDto.class);
        when(field.fieldCode()).thenReturn(code);
        when(field.fieldName()).thenReturn(code);
        when(field.fieldType()).thenReturn(type);
        when(field.textLength()).thenReturn(null);
        return field;
    }
}
