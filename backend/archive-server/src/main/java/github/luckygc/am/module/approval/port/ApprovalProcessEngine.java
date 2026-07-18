package github.luckygc.am.module.approval.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

public interface ApprovalProcessEngine {

    Deployment deploy(String processKey, String processName, String bpmnXml);

    ProcessInstance start(
            String processDefinitionId, String businessKey, Map<String, Object> variables);

    @Nullable ActiveTask findActiveTask(String processInstanceId);

    @Nullable ActiveTask findActiveTaskById(String taskId);

    List<HistoricTask> findHistoricTasks(String processInstanceId);

    List<ProcessComment> findComments(String processInstanceId);

    void addComment(
            @Nullable String taskId,
            String processInstanceId,
            ProcessAction action,
            Long operatorUserId,
            @Nullable String message);

    void completeTask(String taskId, Long userId, Map<String, Object> variables);

    void terminate(String processInstanceId, String reason);

    enum ProcessAction {
        APPROVE,
        REJECT,
        WITHDRAW,
        TERMINATE
    }

    record Deployment(
            String deploymentId, String processDefinitionId, String processDefinitionKey) {}

    record ProcessInstance(String processInstanceId) {}

    record ActiveTask(
            String taskId,
            String processInstanceId,
            String nodeCode,
            String nodeName,
            List<Long> candidateUserIds,
            LocalDateTime createdAt) {

        public ActiveTask {
            candidateUserIds = List.copyOf(candidateUserIds);
        }
    }

    record HistoricTask(
            String taskId,
            String nodeCode,
            String nodeName,
            List<Long> candidateUserIds,
            @Nullable Long assigneeUserId,
            LocalDateTime createdAt,
            @Nullable LocalDateTime completedAt,
            @Nullable String deleteReason) {

        public HistoricTask {
            candidateUserIds = List.copyOf(candidateUserIds);
        }
    }

    record ProcessComment(
            String commentId,
            @Nullable String taskId,
            ProcessAction action,
            Long operatorUserId,
            @Nullable String message,
            LocalDateTime createdAt) {}
}
