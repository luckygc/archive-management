package github.luckygc.am.module.archive.rule;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record ArchiveRuntimeDecision(
        @Nullable Long definitionId,
        String definitionCode,
        ArchiveRuntimeDefinitionKind definitionKind,
        boolean matched,
        List<ArchiveRuntimeActionDecision> actions,
        @Nullable String message,
        ArchiveRuleDecisionSeverity severity,
        boolean blocking,
        @Nullable String skippedReason) {}
