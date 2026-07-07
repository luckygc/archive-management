package github.luckygc.am.module.archive.rule;

public enum ArchiveRuleEffectType {
    VALIDATION_ERROR,
    WARNING,
    SUGGEST_VALUE,
    DERIVED_VALUE,
    REQUIRE_REVIEW,
    REQUIRE_QUALITY_CHECK,
    DENY_ACCESS,
    MASK_FIELD,
    INCLUDE_IN_PACKAGE
}
