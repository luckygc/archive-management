package github.luckygc.am.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

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
}
