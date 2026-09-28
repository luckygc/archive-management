package github.luckygc.am.module.approval.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService.ApprovalWorkflowDefinitionResponse;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalWorkflowGraph;

@DisplayName("审批流定义局部更新 HTTP 入口")
class ApprovalWorkflowDefinitionPatchControllerTests {

    private final ApprovalWorkflowDefinitionService service =
            mock(ApprovalWorkflowDefinitionService.class);
    private final ApprovalWorkflowController controller =
            new ApprovalWorkflowController(service, mock(ApprovalWorkflowInstanceService.class));

    @Test
    @DisplayName("只接受 Merge Patch 并省略不存在的发布版本")
    void patchUsesMergePatchMediaType() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 29, 6, 0);
        when(service.updateDefinition(eq(1L), any(), eq(9L)))
                .thenReturn(
                        new ApprovalWorkflowDefinitionResponse(
                                1L,
                                "contract",
                                "合同审批",
                                "contract",
                                true,
                                1,
                                null,
                                new ApprovalWorkflowGraph(List.of(), List.of()),
                                now,
                                now));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(
                        patch("/approval-workflow-definitions/1")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"graph\":{\"edges\":[]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedVersionId").doesNotExist());
        verify(service).updateDefinition(eq(1L), any(), eq(9L));
    }

    @Test
    @DisplayName("普通 JSON 请求被拒绝")
    void patchRejectsOrdinaryJson() throws Exception {
        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(
                        patch("/approval-workflow-definitions/1")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"definitionName\":\"合同审批\"}"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(service);
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken(
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return 9L;
                    }

                    @Override
                    public String displayName() {
                        return "管理员";
                    }
                },
                null);
    }
}
