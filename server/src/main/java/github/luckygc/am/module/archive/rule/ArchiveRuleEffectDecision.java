package github.luckygc.am.module.archive.rule;

import java.util.Map;

public record ArchiveRuleEffectDecision(ArchiveRuleEffectType effectType, Map<String, Object> params) {}
