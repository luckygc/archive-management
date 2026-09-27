package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import github.luckygc.am.module.archive.rule.ArchiveRuleDecisionSeverity;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeBlockedException;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;

@DisplayName("运行时配置 HTTP 错误响应")
class ArchiveRuntimeControllerProblemDetailTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务阻断返回稳定错误码、原因和安全定义摘要")
    void blockedExecutionUsesStableProblemDetail() {
        ArchiveRuntimeDecision decision =
                new ArchiveRuntimeDecision(
                        3L,
                        "archive-year-required",
                        ArchiveRuntimeDefinitionKind.CONSTRAINT,
                        false,
                        List.of(),
                        "归档年度不能为空",
                        ArchiveRuleDecisionSeverity.ERROR,
                        true,
                        "约束断言未满足");
        ArchiveRuntimeBlockedException exception =
                new ArchiveRuntimeBlockedException(
                        new ArchiveRuntimeExecutionResult(
                                Map.of("metadata.secret", "不得回显"),
                                Map.of(),
                                List.of(decision),
                                List.of(),
                                true));

        var response =
                handler.handleBadRequestException(
                        exception, new MockHttpServletRequest("POST", "/api/v1/archive-items"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "FAILED_PRECONDITION")
                .containsEntry("reason", "ARCHIVE_RUNTIME_BLOCKED")
                .containsEntry("path", "/api/v1/archive-items");
        assertThat(response.getBody().getDetail())
                .contains("archive-year-required")
                .contains("归档年度不能为空")
                .doesNotContain("不得回显");
    }
}
