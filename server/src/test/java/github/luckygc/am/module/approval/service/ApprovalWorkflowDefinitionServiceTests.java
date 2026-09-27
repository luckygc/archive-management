package github.luckygc.am.module.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.approval.ApprovalAction;
import github.luckygc.am.module.approval.ApprovalCandidateStrategy;
import github.luckygc.am.module.approval.ApprovalNodeType;
import github.luckygc.am.module.approval.ApprovalWorkflowDefinition;
import github.luckygc.am.module.approval.ApprovalWorkflowDefinitionVersion;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine;
import github.luckygc.am.module.approval.repository.ApprovalWorkflowDefinitionDataRepository;
import github.luckygc.am.module.approval.repository.ApprovalWorkflowDefinitionVersionDataRepository;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService.ApprovalWorkflowDefinitionVersionResponse;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowEdge;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowNode;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalWorkflowGraph;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("审批流定义服务")
class ApprovalWorkflowDefinitionServiceTests {

    private ApprovalWorkflowDefinitionDataRepository definitionRepository;
    private ApprovalWorkflowDefinitionVersionDataRepository versionRepository;
    private ApprovalProcessEngine processEngine;
    private AuthenticationUserManagementService userManagementService;
    private ApprovalWorkflowDefinitionService service;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        definitionRepository = mock(ApprovalWorkflowDefinitionDataRepository.class);
        versionRepository = mock(ApprovalWorkflowDefinitionVersionDataRepository.class);
        processEngine = mock(ApprovalProcessEngine.class);
        userManagementService = mock(AuthenticationUserManagementService.class);
        jsonMapper = JsonMapper.builder().build();
        service =
                new ApprovalWorkflowDefinitionService(
                        definitionRepository,
                        versionRepository,
                        processEngine,
                        new ApprovalBpmnXmlGenerator(),
                        new ApprovalWorkflowGraphValidator(),
                        mock(AuthorizationPermissionService.class),
                        userManagementService,
                        jsonMapper,
                        Clock.fixed(Instant.parse("2026-07-17T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("发布定义时冻结流程图布局并保存 Flowable 标识")
    void publishShouldPersistImmutableGraphAndFlowableMapping() {
        ApprovalWorkflowDefinition definition = definition();
        when(definitionRepository.findById(1L)).thenReturn(java.util.Optional.of(definition));
        when(versionRepository.findByDefinitionId(1L)).thenReturn(List.of());
        when(processEngine.deploy(any(), any(), any()))
                .thenReturn(
                        new ApprovalProcessEngine.Deployment(
                                "deployment-1", "process-1:1", "approval_1"));
        when(versionRepository.insert(any()))
                .thenAnswer(
                        invocation -> {
                            ApprovalWorkflowDefinitionVersion version = invocation.getArgument(0);
                            version.setId(10L);
                            version.setCreatedAt(LocalDateTime.of(2026, 7, 17, 1, 0));
                            return version;
                        });
        when(definitionRepository.update(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalWorkflowDefinitionVersionResponse response = service.publishDefinition(1L, 9L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.graph().nodes()).extracting("x").contains(100, 200, 300);
        assertThat(definition.getPublishedVersionId()).isEqualTo(10L);
        verify(userManagementService).requireEnabledUsers(List.of(20L));
        verify(processEngine).deploy(any(), any(), any());
        verify(definitionRepository).update(definition);
    }

    private ApprovalWorkflowDefinition definition() {
        ApprovalWorkflowDefinition definition = new ApprovalWorkflowDefinition();
        definition.setId(1L);
        definition.setDefinitionCode("contract_approval");
        definition.setDefinitionName("合同审批");
        definition.setBusinessType("contract");
        definition.setEnabled(true);
        definition.setGraphJson(jsonMapper.writeValueAsString(graph()));
        definition.setCreatedAt(LocalDateTime.of(2026, 7, 17, 0, 0));
        definition.setUpdatedAt(LocalDateTime.of(2026, 7, 17, 0, 0));
        return definition;
    }

    private ApprovalWorkflowGraph graph() {
        return new ApprovalWorkflowGraph(
                List.of(
                        simpleNode("start", "开始", ApprovalNodeType.START, 100),
                        new ApprovalFlowNode(
                                "review",
                                "审核",
                                ApprovalNodeType.APPROVAL,
                                200,
                                100,
                                ApprovalCandidateStrategy.SPECIFIED_USERS,
                                List.of(20L),
                                List.of(ApprovalAction.APPROVE, ApprovalAction.REJECT)),
                        simpleNode("end", "结束", ApprovalNodeType.END, 300)),
                List.of(
                        new ApprovalFlowEdge("flow_start_review", "start", "review", false, null),
                        new ApprovalFlowEdge("flow_review_end", "review", "end", false, null)));
    }

    private ApprovalFlowNode simpleNode(String code, String name, ApprovalNodeType type, int x) {
        return new ApprovalFlowNode(code, name, type, x, 100, null, List.of(), List.of());
    }
}
