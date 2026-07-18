package github.luckygc.am.module.archive.mapper;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;

public record ArchiveRuntimeExecutionCriteria(
        Long schemeVersionId,
        ArchiveRuntimeTriggerPoint triggerPoint,
        @Nullable String fondsCode,
        @Nullable String categoryCode,
        @Nullable String archiveLevel) {}
