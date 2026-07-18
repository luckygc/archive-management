package github.luckygc.am.module.archive.rule.service;

import java.util.List;

import github.luckygc.am.common.exception.BadRequestException;

public class ArchiveRuntimeBlockedException extends BadRequestException {

    private final ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult result;

    public ArchiveRuntimeBlockedException(
            ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult result) {
        super(message(result), List.of(), "FAILED_PRECONDITION", "ARCHIVE_RUNTIME_BLOCKED");
        this.result = result;
    }

    public ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult result() {
        return result;
    }

    private static String message(
            ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult result) {
        String summaries =
                result.decisions().stream()
                        .filter(decision -> decision.blocking())
                        .map(
                                decision ->
                                        decision.definitionCode()
                                                + (decision.message() == null
                                                        ? ""
                                                        : "：" + decision.message()))
                        .collect(java.util.stream.Collectors.joining("；"));
        return summaries.isBlank() ? "操作被运行时约束或规则阻断" : "操作被运行时配置阻断：" + summaries;
    }
}
