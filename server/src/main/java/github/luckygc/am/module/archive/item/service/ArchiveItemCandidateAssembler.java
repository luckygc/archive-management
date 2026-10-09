package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;

/** 组装运行时事实并转换规则产出的候选值，规则执行及写入仍由业务服务负责。 */
final class ArchiveItemCandidateAssembler {

    private final ArchiveItemFieldValueConverter fieldValueConverter;

    ArchiveItemCandidateAssembler(ArchiveItemFieldValueConverter fieldValueConverter) {
        this.fieldValueConverter = fieldValueConverter;
    }

    ArchiveRuntimeExecutionRequest executionRequest(ItemPolicyCommand command, LocalDateTime now) {
        ItemCandidate candidate = command.candidate();
        Map<String, @Nullable Object> facts = new LinkedHashMap<>();
        facts.put("item.id", command.itemId());
        facts.put("item.fondsCode", command.fondsCode());
        facts.put("item.fondsName", command.fondsName());
        facts.put("item.categoryCode", command.category().categoryCode());
        facts.put("item.categoryName", command.category().categoryName());
        facts.put("item.archiveNo", candidate.archiveNo());
        facts.put("item.archiveYear", candidate.archiveYear());
        facts.put("item.securityLevelId", candidate.securityLevelId());
        facts.put("item.retentionPeriodId", candidate.retentionPeriodId());
        addFieldFacts(facts, "metadata.", command.fields(), candidate.dynamicFields());
        addFieldFacts(facts, "physical.", command.physicalFields(), candidate.physicalFields());
        facts.put("context.userId", command.userId());
        facts.put("context.now", now);
        facts.put("context.operation", command.triggerPoint().name());
        return new ArchiveRuntimeExecutionRequest(
                command.triggerPoint(),
                command.fondsCode(),
                command.category().categoryCode(),
                ArchiveLevel.ITEM,
                "ARCHIVE_ITEM",
                command.itemId(),
                facts,
                command.userId());
    }

    ItemCandidate finalCandidate(
            ArchiveRuntimeExecutionResult result,
            List<ArchiveFieldDto> fields,
            List<ArchiveFieldDto> physicalFields) {
        Map<String, @Nullable Object> facts = result.candidateFacts();
        Map<String, @Nullable Object> dynamicFields =
                fieldValueConverter.convertFields(
                        fields, fieldsFromFacts(facts, "metadata.", fields), "dynamicFields");
        Map<String, @Nullable Object> physicalFieldValues =
                fieldValueConverter.convertFields(
                        physicalFields,
                        fieldsFromFacts(facts, "physical.", physicalFields),
                        "physicalFields");
        return new ItemCandidate(
                stringFact(facts, "item.archiveNo"),
                intFact(facts, "item.archiveYear"),
                longFact(facts, "item.securityLevelId"),
                longFact(facts, "item.retentionPeriodId"),
                dynamicFields,
                physicalFieldValues);
    }

    private void addFieldFacts(
            Map<String, @Nullable Object> facts,
            String prefix,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> values) {
        fields.forEach(
                field -> facts.put(prefix + field.fieldCode(), values.get(field.fieldCode())));
    }

    private Map<String, @Nullable Object> fieldsFromFacts(
            Map<String, @Nullable Object> facts, String prefix, List<ArchiveFieldDto> fields) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        fields.forEach(
                field -> values.put(field.fieldCode(), facts.get(prefix + field.fieldCode())));
        return values;
    }

    private @Nullable String stringFact(Map<String, @Nullable Object> facts, String field) {
        Object value = facts.get(field);
        return value == null ? null : StringUtils.trimToNull(value.toString());
    }

    private int intFact(Map<String, @Nullable Object> facts, String field) {
        Object value = facts.get(field);
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BadRequestException("运行时规则字段值类型不兼容：" + field);
        }
    }

    private @Nullable Long longFact(Map<String, @Nullable Object> facts, String field) {
        Object value = facts.get(field);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new BadRequestException("运行时规则字段值类型不兼容：" + field);
        }
    }

    record ItemCandidate(
            @Nullable String archiveNo,
            int archiveYear,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            Map<String, @Nullable Object> dynamicFields,
            Map<String, @Nullable Object> physicalFields) {}

    record ItemPolicyCommand(
            ArchiveRuntimeTriggerPoint triggerPoint,
            @Nullable Long itemId,
            String fondsCode,
            String fondsName,
            ArchiveCategoryDto category,
            ItemCandidate candidate,
            List<ArchiveFieldDto> fields,
            List<ArchiveFieldDto> physicalFields,
            Long userId) {}
}
