package github.luckygc.am.module.archive.rule;

import java.util.Map;

public record ArchiveRuntimeActionDecision(
        ArchiveRuntimeActionType actionType, Map<String, Object> params) {}
