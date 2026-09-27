package github.luckygc.am.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.approval.ApprovalAction;
import github.luckygc.am.module.approval.ApprovalCandidateStrategy;
import github.luckygc.am.module.approval.ApprovalNodeType;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine.ProcessAction;
import github.luckygc.am.module.approval.service.ApprovalBpmnXmlGenerator;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowEdge;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalFlowNode;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalWorkflowGraph;
import github.luckygc.am.test.PostgreSqlContainerTest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.process.definition-cache-limit=16"
        })
@DisplayName("Flowable 引擎兼容性")
class FlowableCompatibilityTests extends PostgreSqlContainerTest {

    @Autowired private RepositoryService repositoryService;

    @Autowired private RuntimeService runtimeService;

    @Autowired private TaskService taskService;

    @Autowired private ApprovalProcessEngine approvalProcessEngine;

    @Autowired private ApprovalBpmnXmlGenerator bpmnXmlGenerator;

    @Test
    @DisplayName("流程定义可以部署、启动并查询候选用户任务")
    void deployStartAndQueryUserTask() {
        Deployment deployment = null;
        String deploymentId = null;
        String processInstanceId = null;
        try {
            deployment =
                    repositoryService
                            .createDeployment()
                            .name("flowable-compatibility")
                            .addClasspathResource("processes/flowable-compatibility.bpmn20.xml")
                            .deploy();
            deploymentId = deployment.getId();

            ProcessInstance instance =
                    runtimeService.startProcessInstanceByKey(
                            "flowableCompatibilityProcess",
                            Map.of("businessKey", "compatibility-business"));
            processInstanceId = instance.getId();

            Task task =
                    taskService
                            .createTaskQuery()
                            .processInstanceId(processInstanceId)
                            .taskCandidateUser("compatibility-user")
                            .singleResult();

            assertThat(deploymentId).isNotBlank();
            assertThat(instance.getProcessDefinitionId()).isNotBlank();
            assertThat(task).isNotNull();
            assertThat(task.getName()).isEqualTo("Review");
        } finally {
            if (deploymentId != null) {
                repositoryService.deleteDeployment(deploymentId, true);
            }
        }
        assertThat(repositoryService.createDeploymentQuery().deploymentId(deploymentId).count())
                .isZero();
        assertThat(
                        runtimeService
                                .createProcessInstanceQuery()
                                .processInstanceId(processInstanceId)
                                .count())
                .isZero();
        assertThat(taskService.createTaskQuery().processInstanceId(processInstanceId).count())
                .isZero();
    }

    @Test
    @DisplayName("项目适配层可以部署、推进并结束顺序审批")
    void approvalAdapterShouldDeployStartAndCompleteSequentialProcess() {
        ApprovalProcessEngine.Deployment deployment =
                approvalProcessEngine.deploy(
                        "approval_adapter_test",
                        "适配层测试",
                        bpmnXmlGenerator.generate(
                                "approval_adapter_test",
                                "适配层测试",
                                new ApprovalWorkflowGraph(
                                        List.of(
                                                new ApprovalFlowNode(
                                                        "start",
                                                        "开始",
                                                        ApprovalNodeType.START,
                                                        100,
                                                        100,
                                                        null,
                                                        List.of(),
                                                        List.of()),
                                                new ApprovalFlowNode(
                                                        "review",
                                                        "审核",
                                                        ApprovalNodeType.APPROVAL,
                                                        200,
                                                        100,
                                                        ApprovalCandidateStrategy.SPECIFIED_USERS,
                                                        List.of(99L),
                                                        List.of(
                                                                ApprovalAction.APPROVE,
                                                                ApprovalAction.REJECT)),
                                                new ApprovalFlowNode(
                                                        "end",
                                                        "结束",
                                                        ApprovalNodeType.END,
                                                        300,
                                                        100,
                                                        null,
                                                        List.of(),
                                                        List.of())),
                                        List.of(
                                                new ApprovalFlowEdge(
                                                        "flow_start_review",
                                                        "start",
                                                        "review",
                                                        false,
                                                        null),
                                                new ApprovalFlowEdge(
                                                        "flow_review_end",
                                                        "review",
                                                        "end",
                                                        false,
                                                        null)))));
        try {
            ApprovalProcessEngine.ProcessInstance instance =
                    approvalProcessEngine.start(
                            deployment.processDefinitionId(), "test:1", Map.of());
            ApprovalProcessEngine.ActiveTask task =
                    approvalProcessEngine.findActiveTask(instance.processInstanceId());

            assertThat(task).isNotNull();
            assertThat(task.candidateUserIds()).containsExactly(99L);

            approvalProcessEngine.addComment(
                    task.taskId(), instance.processInstanceId(), ProcessAction.APPROVE, 99L, "同意");
            approvalProcessEngine.completeTask(task.taskId(), 99L, Map.of("approved", true));

            assertThat(approvalProcessEngine.findActiveTask(instance.processInstanceId())).isNull();
            assertThat(approvalProcessEngine.findHistoricTasks(instance.processInstanceId()))
                    .hasSize(1);
            assertThat(approvalProcessEngine.findComments(instance.processInstanceId()))
                    .extracting("message")
                    .containsExactly("同意");
        } finally {
            repositoryService.deleteDeployment(deployment.deploymentId(), true);
        }
    }
}
