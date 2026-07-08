package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService.ExecuteArchiveRulesRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService.SearchArchiveRuleTracesRequest;
import github.luckygc.am.module.archive.rule.web.ArchiveRuleController;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案规则 HTTP 入口错误响应")
class ArchiveRuleControllerProblemDetailTests {

    private final ArchiveLocalRuleService ruleService = mock(ArchiveLocalRuleService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveRuleController controller =
            new ArchiveRuleController(ruleService, permissionService);
    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("规则试算使用认证用户 ID 覆盖请求体用户 ID")
    void executeRulesShouldUseAuthenticatedUserId() {
        when(ruleService.executeRules(any())).thenReturn(List.of());

        controller.executeRules(
                new ExecuteArchiveRulesRequest(
                        1L,
                        "BEFORE_SAVE",
                        "F001",
                        "C001",
                        "ARCHIVE_ITEM",
                        ArchiveLevel.ITEM,
                        null,
                        Map.of("fixed.archiveYear", 2026),
                        false,
                        true,
                        999L),
                auth(9L));

        verify(permissionService)
                .requirePermission(9L, AuthorizationPermissionCode.ARCHIVE_GOVERNANCE_MANAGE);
        ArgumentCaptor<ExecuteArchiveRulesRequest> captor =
                ArgumentCaptor.forClass(ExecuteArchiveRulesRequest.class);
        verify(ruleService).executeRules(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(9L);
        assertThat(captor.getValue().recordTrace()).isTrue();
    }

    @Test
    @DisplayName("追踪查询缺少治理权限时输出 ProblemDetail")
    void searchRuleTracesShouldUseProblemDetailWhenPermissionDenied() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足"))
                .when(permissionService)
                .requirePermission(9L, AuthorizationPermissionCode.ARCHIVE_GOVERNANCE_MANAGE);

        assertThatThrownBy(
                        () ->
                                controller.searchRuleTraces(
                                        new SearchArchiveRuleTracesRequest(
                                                null, null, null, null, null, 100, null),
                                        auth(9L)))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                            var response =
                                    exceptionHandler.handleResponseStatusException(
                                            exception,
                                            new MockHttpServletRequest(
                                                    "POST", "/api/v1/archive-rule-traces:search"));
                            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                            assertThat(response.getBody()).isNotNull();
                            assertThat(response.getBody().getTitle()).isEqualTo("无访问权限");
                            assertThat(response.getBody().getProperties())
                                    .containsEntry("code", "PERMISSION_DENIED")
                                    .containsEntry("reason", "PERMISSION_DENIED_ERROR")
                                    .containsEntry("path", "/api/v1/archive-rule-traces:search");
                        });
        verifyNoInteractions(ruleService);
    }

    private TestingAuthenticationToken auth(Long userId) {
        return new TestingAuthenticationToken(
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return userId;
                    }

                    @Override
                    public String displayName() {
                        return "档案管理员";
                    }
                },
                null);
    }
}
