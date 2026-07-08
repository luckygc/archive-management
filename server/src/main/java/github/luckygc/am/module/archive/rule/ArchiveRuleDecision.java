package github.luckygc.am.module.archive.rule;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record ArchiveRuleDecision(
        @Nullable Long ruleId,
        String ruleCode,
        ArchiveRuleType ruleType,
        boolean matched,
        List<ArchiveRuleEffectDecision> effects,
        @Nullable String message,
        ArchiveRuleDecisionSeverity severity,
        boolean blocking,
        @Nullable String skippedReason) {}
