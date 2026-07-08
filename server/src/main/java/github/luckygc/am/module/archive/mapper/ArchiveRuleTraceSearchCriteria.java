package github.luckygc.am.module.archive.mapper;

import org.jspecify.annotations.Nullable;

public record ArchiveRuleTraceSearchCriteria(
        @Nullable Long schemeVersionId,
        @Nullable String triggerCode,
        @Nullable String objectTypeCode,
        @Nullable Long objectId,
        @Nullable String ruleType,
        int limit) {}
