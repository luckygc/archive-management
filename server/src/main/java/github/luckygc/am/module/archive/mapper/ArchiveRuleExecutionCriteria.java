package github.luckygc.am.module.archive.mapper;

import org.jspecify.annotations.Nullable;

public record ArchiveRuleExecutionCriteria(
        Long schemeVersionId,
        String triggerCode,
        @Nullable String fondsCode,
        @Nullable String categoryCode,
        @Nullable String objectTypeCode,
        @Nullable String archiveLevel,
        @Nullable String eventCode) {}
