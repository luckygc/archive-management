package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.rule.ArchiveRuleDecisionSeverity;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTrace;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeTraceDataRepository;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;

@DisplayName("运行时决策追踪")
class ArchiveRuntimeTraceServiceTests {

    private ArchiveRuntimeTraceDataRepository traceRepository;
    private ArchiveRuntimeTraceService service;

    @BeforeEach
    void setUp() {
        traceRepository = mock(ArchiveRuntimeTraceDataRepository.class);
        service =
                new ArchiveRuntimeTraceService(
                        traceRepository,
                        mock(ArchiveRuleMapper.class),
                        mock(ArchiveDataScopeService.class),
                        mock(ArchiveCategoryService.class));
    }

    @Test
    @DisplayName("成功执行只保存动作类型和目标字段，不保存候选值")
    void successfulExecutionStoresSafeActionSummary() {
        ArchiveRuntimeDecision decision =
                new ArchiveRuntimeDecision(
                        3L,
                        "normalize-title",
                        ArchiveRuntimeDefinitionKind.RULE,
                        true,
                        List.of(
                                new ArchiveRuntimeActionDecision(
                                        ArchiveRuntimeActionType.SET_FIELD,
                                        Map.of("field", "metadata.title", "value", "不得进入追踪的业务内容"))),
                        null,
                        ArchiveRuleDecisionSeverity.INFO,
                        false,
                        null);

        service.saveSuccessfulExecution(
                request(),
                new ArchiveRuntimeExecutionResult(
                        Map.of("metadata.title", "不得进入追踪的业务内容"),
                        Map.of(),
                        List.of(decision),
                        List.of(),
                        false),
                99L);

        ArgumentCaptor<ArchiveRuntimeTrace> captor =
                ArgumentCaptor.forClass(ArchiveRuntimeTrace.class);
        verify(traceRepository).insert(captor.capture());
        ArchiveRuntimeTrace trace = captor.getValue();
        assertThat(trace.getObjectId()).isEqualTo(99L);
        assertThat(trace.getDefinitionCode()).isEqualTo("normalize-title");
        assertThat(trace.getActionJson())
                .containsExactly(Map.of("actionType", "SET_FIELD", "field", "metadata.title"));
        assertThat(trace.getActionJson().toString()).doesNotContain("业务内容");
    }

    @Test
    @DisplayName("阻断结果不能作为成功事务追踪写入")
    void blockingResultIsRejected() {
        ArchiveRuntimeExecutionResult result =
                new ArchiveRuntimeExecutionResult(Map.of(), Map.of(), List.of(), List.of(), true);

        assertThatThrownBy(() -> service.saveSuccessfulExecution(request(), result, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ArchiveRuntimeExecutionRequest request() {
        return new ArchiveRuntimeExecutionRequest(
                1L,
                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                "F001",
                "DOC",
                ArchiveLevel.ITEM,
                "ARCHIVE_ITEM",
                null,
                Map.of(),
                7L);
    }
}
