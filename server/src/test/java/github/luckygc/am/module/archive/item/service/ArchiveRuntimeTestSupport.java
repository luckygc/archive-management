package github.luckygc.am.module.archive.item.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeTraceService;

final class ArchiveRuntimeTestSupport {

    private ArchiveRuntimeTestSupport() {}

    static ArchiveRuntimeExecutionService passthroughExecutionService() {
        ArchiveRuntimeExecutionService service = mock(ArchiveRuntimeExecutionService.class);
        when(service.enforce(any(ArchiveRuntimeExecutionRequest.class)))
                .thenAnswer(
                        invocation -> {
                            ArchiveRuntimeExecutionRequest request = invocation.getArgument(0);
                            return new ArchiveRuntimeExecutionResult(
                                    request.candidateFacts(),
                                    Map.of(),
                                    List.of(),
                                    List.of(),
                                    false);
                        });
        return service;
    }

    static ArchiveRuntimeTraceService traceService() {
        return mock(ArchiveRuntimeTraceService.class);
    }
}
