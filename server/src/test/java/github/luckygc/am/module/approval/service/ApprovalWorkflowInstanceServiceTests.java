package github.luckygc.am.module.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.module.approval.ApprovalWorkflowDefinition;
import github.luckygc.am.module.approval.ApprovalWorkflowDefinitionVersion;
import github.luckygc.am.module.approval.ApprovalWorkflowInstance;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine;
import github.luckygc.am.module.approval.repository.ApprovalWorkflowInstanceDataRepository;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService.ApprovalWorkflowInstanceResponse;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService.StartApprovalWorkflowInstanceRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.todo.service.UnifiedTodoService;
import github.luckygc.am.module.todo.service.UnifiedTodoService.DispatchUnifiedTodoCommand;

@DisplayName("审批流实例服务")
class ApprovalWorkflowInstanceServiceTests {

    private ApprovalWorkflowDefinitionService definitionService;
    private ApprovalWorkflowInstanceDataRepository instanceRepository;
    private ApprovalProcessEngine processEngine;
    private UnifiedTodoService todoService;
    private ApprovalWorkflowInstanceService service;

    @BeforeEach
    void setUp() {
        definitionService = mock(ApprovalWorkflowDefinitionService.class);
        instanceRepository = mock(ApprovalWorkflowInstanceDataRepository.class);
        processEngine = mock(ApprovalProcessEngine.class);
        todoService = mock(UnifiedTodoService.class);
        service =
                new ApprovalWorkflowInstanceService(
                        definitionService,
                        instanceRepository,
                        processEngine,
                        todoService,
                        mock(AuthorizationPermissionService.class),
                        Clock.fixed(Instant.parse("2026-07-17T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("发起实例后按 Flowable 实际候选人投递统一待办")
    void startShouldDispatchUnifiedTodosForActiveCandidates() {
        ApprovalWorkflowDefinition definition = definition();
        ApprovalWorkflowDefinitionVersion version = version();
        when(definitionService.loadDefinitionForStart(1L)).thenReturn(definition);
        when(definitionService.loadPublishedVersion(definition)).thenReturn(version);
        when(processEngine.start(eq("process-1:1"), eq("contract:C-1"), any()))
                .thenReturn(new ApprovalProcessEngine.ProcessInstance("instance-1"));
        when(processEngine.findActiveTask("instance-1"))
                .thenReturn(
                        new ApprovalProcessEngine.ActiveTask(
                                "task-1",
                                "instance-1",
                                "review",
                                "审核",
                                List.of(20L),
                                LocalDateTime.of(2026, 7, 17, 1, 0)));
        when(instanceRepository.insert(any()))
                .thenAnswer(
                        invocation -> {
                            ApprovalWorkflowInstance instance = invocation.getArgument(0);
                            instance.setId(30L);
                            instance.setCreatedAt(LocalDateTime.of(2026, 7, 17, 1, 0));
                            instance.setUpdatedAt(LocalDateTime.of(2026, 7, 17, 1, 0));
                            return instance;
                        });
        when(instanceRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalWorkflowInstanceResponse response =
                service.startInstance(
                        new StartApprovalWorkflowInstanceRequest(
                                1L, "contract", "C-1", "合同 C-1 审批", Map.of("amount", "100")),
                        9L);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.currentNodeCode()).isEqualTo("review");
        ArgumentCaptor<DispatchUnifiedTodoCommand> commandCaptor =
                ArgumentCaptor.forClass(DispatchUnifiedTodoCommand.class);
        verify(todoService).dispatch(commandCaptor.capture());
        assertThat(commandCaptor.getValue().sourceTaskId()).isEqualTo("task-1");
        assertThat(commandCaptor.getValue().assigneeUserIds()).containsExactly(20L);
    }

    private ApprovalWorkflowDefinition definition() {
        ApprovalWorkflowDefinition definition = new ApprovalWorkflowDefinition();
        definition.setId(1L);
        definition.setBusinessType("contract");
        definition.setEnabled(true);
        definition.setPublishedVersionId(10L);
        return definition;
    }

    private ApprovalWorkflowDefinitionVersion version() {
        ApprovalWorkflowDefinitionVersion version = new ApprovalWorkflowDefinitionVersion();
        version.setId(10L);
        version.setFlowableProcessDefinitionId("process-1:1");
        version.setGraphJson("{\"nodes\":[],\"edges\":[]}");
        return version;
    }
}
