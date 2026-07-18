package github.luckygc.am.module.archive.rule.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeTraceService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeTraceService.SearchArchiveRuntimeTracesRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("运行时配置 HTTP 入口")
class ArchiveRuntimeControllerTests {

    private final ArchiveRuntimeDefinitionService definitionService =
            mock(ArchiveRuntimeDefinitionService.class);
    private final ArchiveRuntimeFieldCatalogService fieldCatalogService =
            mock(ArchiveRuntimeFieldCatalogService.class);
    private final ArchiveRuntimeExecutionService executionService =
            mock(ArchiveRuntimeExecutionService.class);
    private final ArchiveRuntimeTraceService traceService = mock(ArchiveRuntimeTraceService.class);
    private final ArchiveRuntimeSnapshotService snapshotService =
            mock(ArchiveRuntimeSnapshotService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveRuntimeController controller =
            new ArchiveRuntimeController(
                    definitionService,
                    fieldCatalogService,
                    executionService,
                    traceService,
                    snapshotService,
                    permissionService);

    @Test
    @DisplayName("试运行强制使用认证用户且保持无追踪执行入口")
    void simulationUsesAuthenticatedUser() {
        ArchiveRuntimeExecutionResult response =
                new ArchiveRuntimeExecutionResult(Map.of(), Map.of(), List.of(), List.of(), false);
        when(executionService.simulate(any())).thenReturn(response);

        ArchiveRuntimeExecutionResult actual =
                controller.simulate(
                        new ArchiveRuntimeExecutionRequest(
                                1L,
                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                                "F001",
                                "DOC",
                                ArchiveLevel.ITEM,
                                "ARCHIVE_ITEM",
                                null,
                                Map.of(),
                                999L),
                        auth(9L));

        assertThat(actual).isSameAs(response);
        ArgumentCaptor<ArchiveRuntimeExecutionRequest> captor =
                ArgumentCaptor.forClass(ArchiveRuntimeExecutionRequest.class);
        verify(executionService).simulate(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(9L);
        verify(permissionService)
                .requirePermission(9L, AuthorizationPermissionCode.ARCHIVE_GOVERNANCE_MANAGE);
    }

    @Test
    @DisplayName("追踪接口使用完整版本化 URL 并转发认证用户和游标")
    void traceSearchUsesVersionedUrlAndAuthenticatedUser() throws Exception {
        PageRequest page = PageRequest.ofSize(50);
        when(traceService.listTraces(any(), any()))
                .thenReturn(
                        CursorPageResponse.withCursorValues(
                                List.of(), 50, null, null, null, null, null));

        controller.searchTraces(
                new SearchArchiveRuntimeTracesRequest(
                        1L,
                        ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE,
                        "ARCHIVE_ITEM",
                        3L,
                        null,
                        999L),
                page,
                auth(9L));

        PostMapping mapping =
                ArchiveRuntimeController.class
                        .getMethod(
                                "searchTraces",
                                SearchArchiveRuntimeTracesRequest.class,
                                PageRequest.class,
                                org.springframework.security.core.Authentication.class)
                        .getAnnotation(PostMapping.class);
        assertThat(mapping.value()).containsExactly("/api/v1/archive-runtime-traces:search");
        ArgumentCaptor<SearchArchiveRuntimeTracesRequest> requestCaptor =
                ArgumentCaptor.forClass(SearchArchiveRuntimeTracesRequest.class);
        verify(traceService)
                .listTraces(requestCaptor.capture(), org.mockito.ArgumentMatchers.same(page));
        assertThat(requestCaptor.getValue().userId()).isEqualTo(9L);
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
