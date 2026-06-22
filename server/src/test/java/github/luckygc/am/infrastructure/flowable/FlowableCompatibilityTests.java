package github.luckygc.am.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "spring.ai.model.deepseek.autoconfigure.enabled=false",
            "spring.flyway.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.process.definition-cache-limit=16"
        })
class FlowableCompatibilityTests {

    @Autowired private RepositoryService repositoryService;

    @Autowired private RuntimeService runtimeService;

    @Autowired private TaskService taskService;

    @Test
    void deployStartAndQueryUserTask() {
        Deployment deployment = null;
        try {
            deployment =
                    repositoryService
                            .createDeployment()
                            .name("flowable-compatibility")
                            .addClasspathResource("processes/flowable-compatibility.bpmn20.xml")
                            .deploy();

            ProcessInstance instance =
                    runtimeService.startProcessInstanceByKey(
                            "flowableCompatibilityProcess",
                            Map.of("businessKey", "compatibility-business"));

            Task task =
                    taskService
                            .createTaskQuery()
                            .processInstanceId(instance.getId())
                            .taskCandidateUser("compatibility-user")
                            .singleResult();

            assertThat(deployment.getId()).isNotBlank();
            assertThat(instance.getProcessDefinitionId()).isNotBlank();
            assertThat(task).isNotNull();
            assertThat(task.getName()).isEqualTo("Review");
        } finally {
            if (deployment != null) {
                repositoryService.deleteDeployment(deployment.getId(), true);
            }
        }
    }
}
