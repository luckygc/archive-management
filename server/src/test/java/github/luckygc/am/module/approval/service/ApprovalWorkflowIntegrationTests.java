package github.luckygc.am.module.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.approval.ApprovalAction;
import github.luckygc.am.module.approval.ApprovalCandidateStrategy;
import github.luckygc.am.module.approval.ApprovalConditionOperator;
import github.luckygc.am.module.approval.ApprovalInstanceStatus;
import github.luckygc.am.module.approval.ApprovalNodeType;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService.CreateApprovalWorkflowDefinitionRequest;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService.CompleteApprovalWorkflowTaskRequest;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService.StartApprovalWorkflowInstanceRequest;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowCondition;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowEdge;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowNode;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalWorkflowGraph;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.todo.service.UnifiedTodoService;
import github.luckygc.am.test.PostgreSqlContainerTest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false"
        })
@DisplayName("通用审批流闭环")
class ApprovalWorkflowIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private AuthenticationUserDataRepository userRepository;
    @Autowired private AuthorizationRoleDataRepository roleRepository;
    @Autowired private AuthorizationUserRoleRelationDataRepository userRoleRepository;
    @Autowired private ApprovalWorkflowDefinitionService definitionService;
    @Autowired private ApprovalWorkflowInstanceService instanceService;
    @Autowired private UnifiedTodoService todoService;

    private Long userId;

    @BeforeEach
    void setUpUser() {
        AuthenticationUser user = new AuthenticationUser();
        user.setUsername("approval-admin-" + System.nanoTime());
        user.setPassword("not-used");
        user.setDisplayName("审批管理员");
        user = userRepository.insert(user);
        AuthorizationRole role =
                roleRepository.findOptionalByRoleName(
                        AuthorizationPermissionService.SUPER_ADMIN_ROLE_NAME);
        AuthorizationUserRoleRelation relation = new AuthorizationUserRoleRelation();
        relation.setUserId(user.getId());
        relation.setRoleId(role.getId());
        userRoleRepository.insert(relation);
        userId = user.getId();
    }

    @Test
    @DisplayName("发布两级顺序审批后通过统一待办逐级同意并读取 Flowable 历史")
    void shouldPublishStartApproveAndComplete() {
        var definition =
                definitionService.createDefinition(
                        new CreateApprovalWorkflowDefinitionRequest(
                                "contract_approval_" + userId,
                                "合同审批",
                                "contract",
                                sequentialGraph(
                                        approvalNode("department_review", "部门审核"),
                                        approvalNode("archive_review", "档案审核"))),
                        userId);
        var version = definitionService.publishDefinition(definition.id(), userId);

        var instance =
                instanceService.startInstance(
                        new StartApprovalWorkflowInstanceRequest(
                                definition.id(), "contract", "C-1", "合同 C-1 审批", null),
                        userId);
        var firstTodo =
                todoService.listMy(false, PageRequest.ofSize(100), userId).items().getFirst();
        assertThat(firstTodo.sourcePath())
                .isEqualTo("/approval/center?instanceId=" + instance.id());
        Long firstTodoId = firstTodo.id();

        var secondDetail =
                instanceService.approveTask(
                        firstTodoId, new CompleteApprovalWorkflowTaskRequest("部门同意"), userId);
        Long secondTodoId = pendingTodoId();

        var completed =
                instanceService.approveTask(
                        secondTodoId, new CompleteApprovalWorkflowTaskRequest("档案部门同意"), userId);

        assertThat(version.versionNumber()).isEqualTo(1);
        assertThat(secondDetail.instance().currentNodeCode()).isEqualTo("archive_review");
        assertThat(completed.instance().status()).isEqualTo(ApprovalInstanceStatus.APPROVED);
        assertThat(completed.tasks()).hasSize(2);
        assertThat(completed.opinions()).extracting("comment").containsExactly("部门同意", "档案部门同意");
        assertThat(todoService.listMy(true, PageRequest.ofSize(100), userId).items()).hasSize(2);
    }

    @Test
    @DisplayName("业务上下文命中受控条件分支")
    void shouldExecuteControlledConditionalBranch() {
        var definition =
                definitionService.createDefinition(
                        new CreateApprovalWorkflowDefinitionRequest(
                                "conditional_approval_" + userId,
                                "条件审批",
                                "conditional_case",
                                conditionalGraph()),
                        userId);
        definitionService.publishDefinition(definition.id(), userId);
        var instance =
                instanceService.startInstance(
                        new StartApprovalWorkflowInstanceRequest(
                                definition.id(),
                                "conditional_case",
                                "COND-1",
                                "重要档案审批",
                                Map.of("level", "important")),
                        userId);

        var detail =
                instanceService.approveTask(
                        pendingTodoId(), new CompleteApprovalWorkflowTaskRequest("初审通过"), userId);

        assertThat(instance.status()).isEqualTo(ApprovalInstanceStatus.RUNNING);
        assertThat(detail.instance().currentNodeCode()).isEqualTo("manager_review");
    }

    @Test
    @DisplayName("候选用户驳回后终止实例并读取 Flowable 驳回评论")
    void shouldRejectAndTerminateProcess() {
        var definition =
                definitionService.createDefinition(
                        new CreateApprovalWorkflowDefinitionRequest(
                                "reject_approval_" + userId,
                                "驳回测试",
                                "reject_case",
                                sequentialGraph(approvalNode("review", "审核"))),
                        userId);
        definitionService.publishDefinition(definition.id(), userId);
        var instance =
                instanceService.startInstance(
                        new StartApprovalWorkflowInstanceRequest(
                                definition.id(), "reject_case", "R-1", "驳回测试", null),
                        userId);

        var rejected =
                instanceService.rejectTask(
                        pendingTodoId(), new CompleteApprovalWorkflowTaskRequest("材料不完整"), userId);

        assertThat(instance.status()).isEqualTo(ApprovalInstanceStatus.RUNNING);
        assertThat(rejected.instance().status()).isEqualTo(ApprovalInstanceStatus.REJECTED);
        assertThat(rejected.opinions()).extracting("action").containsExactly(ApprovalAction.REJECT);
        assertThat(rejected.opinions()).extracting("comment").containsExactly("材料不完整");
    }

    private Long pendingTodoId() {
        return todoService.listMy(false, PageRequest.ofSize(100), userId).items().getFirst().id();
    }

    private ApprovalWorkflowGraph sequentialGraph(ApprovalFlowNode... approvalNodes) {
        List<ApprovalFlowNode> nodes = new java.util.ArrayList<>();
        List<ApprovalFlowEdge> edges = new java.util.ArrayList<>();
        nodes.add(simpleNode("start", "开始", ApprovalNodeType.START, 100));
        String previous = "start";
        int x = 220;
        for (ApprovalFlowNode node : approvalNodes) {
            nodes.add(
                    new ApprovalFlowNode(
                            node.nodeCode(),
                            node.nodeName(),
                            node.nodeType(),
                            x,
                            120,
                            node.candidateStrategy(),
                            node.candidateUserIds(),
                            node.allowedActions()));
            edges.add(
                    new ApprovalFlowEdge(
                            "flow_" + previous + "_" + node.nodeCode(),
                            previous,
                            node.nodeCode(),
                            false,
                            null));
            previous = node.nodeCode();
            x += 160;
        }
        nodes.add(simpleNode("end", "结束", ApprovalNodeType.END, x));
        edges.add(new ApprovalFlowEdge("flow_" + previous + "_end", previous, "end", false, null));
        return new ApprovalWorkflowGraph(nodes, edges);
    }

    private ApprovalWorkflowGraph conditionalGraph() {
        return new ApprovalWorkflowGraph(
                List.of(
                        simpleNode("start", "开始", ApprovalNodeType.START, 100),
                        approvalNode("initial_review", "初审"),
                        simpleNode(
                                "level_gateway", "级别判断", ApprovalNodeType.EXCLUSIVE_GATEWAY, 380),
                        approvalNode("manager_review", "负责人审批"),
                        approvalNode("archive_review", "档案审批"),
                        simpleNode("end", "结束", ApprovalNodeType.END, 700)),
                List.of(
                        edge("flow_start_initial", "start", "initial_review"),
                        edge("flow_initial_gateway", "initial_review", "level_gateway"),
                        new ApprovalFlowEdge(
                                "flow_gateway_manager",
                                "level_gateway",
                                "manager_review",
                                false,
                                new ApprovalFlowCondition(
                                        "level",
                                        ApprovalConditionOperator.EQUALS,
                                        List.of("important"))),
                        new ApprovalFlowEdge(
                                "flow_gateway_archive",
                                "level_gateway",
                                "archive_review",
                                true,
                                null),
                        edge("flow_manager_end", "manager_review", "end"),
                        edge("flow_archive_end", "archive_review", "end")));
    }

    private ApprovalFlowNode approvalNode(String code, String name) {
        return new ApprovalFlowNode(
                code,
                name,
                ApprovalNodeType.APPROVAL,
                260,
                120,
                ApprovalCandidateStrategy.SPECIFIED_USERS,
                List.of(userId),
                List.of(ApprovalAction.APPROVE, ApprovalAction.REJECT));
    }

    private ApprovalFlowNode simpleNode(String code, String name, ApprovalNodeType type, int x) {
        return new ApprovalFlowNode(code, name, type, x, 120, null, List.of(), List.of());
    }

    private ApprovalFlowEdge edge(String code, String source, String target) {
        return new ApprovalFlowEdge(code, source, target, false, null);
    }
}
