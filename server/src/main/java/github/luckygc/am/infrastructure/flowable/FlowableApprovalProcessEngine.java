package github.luckygc.am.infrastructure.flowable;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLinkInfo;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import github.luckygc.am.module.approval.port.ApprovalProcessEngine;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine.ProcessAction;

@Component
public class FlowableApprovalProcessEngine implements ApprovalProcessEngine {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final Clock clock;

    public FlowableApprovalProcessEngine(
            RepositoryService repositoryService,
            RuntimeService runtimeService,
            TaskService taskService,
            HistoryService historyService,
            Clock clock) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.clock = clock;
    }

    @Override
    public Deployment deploy(String processKey, String processName, String bpmnXml) {
        org.flowable.engine.repository.Deployment deployment =
                repositoryService
                        .createDeployment()
                        .name(processName)
                        .addString(processKey + ".bpmn20.xml", bpmnXml)
                        .deploy();
        ProcessDefinition definition =
                repositoryService
                        .createProcessDefinitionQuery()
                        .deploymentId(deployment.getId())
                        .singleResult();
        if (definition == null) {
            throw new IllegalStateException("Flowable 部署未生成流程定义");
        }
        return new Deployment(deployment.getId(), definition.getId(), definition.getKey());
    }

    @Override
    public ApprovalProcessEngine.ProcessInstance start(
            String processDefinitionId, String businessKey, Map<String, Object> variables) {
        org.flowable.engine.runtime.ProcessInstance instance =
                runtimeService
                        .createProcessInstanceBuilder()
                        .processDefinitionId(processDefinitionId)
                        .businessKey(businessKey)
                        .variables(variables)
                        .start();
        return new ApprovalProcessEngine.ProcessInstance(instance.getProcessInstanceId());
    }

    @Override
    public @Nullable ActiveTask findActiveTask(String processInstanceId) {
        Task task =
                taskService
                        .createTaskQuery()
                        .processInstanceId(processInstanceId)
                        .active()
                        .singleResult();
        return task == null ? null : toActiveTask(task);
    }

    @Override
    public @Nullable ActiveTask findActiveTaskById(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        return task == null ? null : toActiveTask(task);
    }

    @Override
    public List<HistoricTask> findHistoricTasks(String processInstanceId) {
        return historyService
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime()
                .asc()
                .list()
                .stream()
                .map(this::toHistoricTask)
                .toList();
    }

    @Override
    public List<ProcessComment> findComments(String processInstanceId) {
        return taskService.getProcessInstanceComments(processInstanceId).stream()
                .filter(comment -> StringUtils.isNotBlank(comment.getType()))
                .map(this::toComment)
                .sorted(Comparator.comparing(ProcessComment::createdAt))
                .toList();
    }

    @Override
    public void addComment(
            @Nullable String taskId,
            String processInstanceId,
            ProcessAction action,
            Long operatorUserId,
            @Nullable String message) {
        Comment comment =
                taskService.addComment(
                        taskId,
                        processInstanceId,
                        action.name(),
                        StringUtils.isBlank(message) ? null : message.trim());
        comment.setUserId(operatorUserId.toString());
        taskService.saveComment(comment);
    }

    @Override
    public void completeTask(String taskId, Long userId, Map<String, Object> variables) {
        taskService.complete(taskId, userId.toString(), variables);
    }

    @Override
    public void terminate(String processInstanceId, String reason) {
        try {
            runtimeService.deleteProcessInstance(processInstanceId, reason);
        } catch (FlowableObjectNotFoundException ignored) {
            // 项目实例可能在引擎自然结束后收敛状态，终止操作保持幂等。
        }
    }

    private ActiveTask toActiveTask(Task task) {
        return new ActiveTask(
                task.getId(),
                task.getProcessInstanceId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                candidateUserIds(taskService.getIdentityLinksForTask(task.getId())),
                toLocalDateTime(task.getCreateTime()));
    }

    private HistoricTask toHistoricTask(HistoricTaskInstance task) {
        return new HistoricTask(
                task.getId(),
                task.getTaskDefinitionKey(),
                task.getName(),
                candidateUserIds(historyService.getHistoricIdentityLinksForTask(task.getId())),
                parseUserId(
                        task.getCompletedBy() == null ? task.getAssignee() : task.getCompletedBy()),
                toLocalDateTime(task.getStartTime()),
                task.getEndTime() == null ? null : toLocalDateTime(task.getEndTime()),
                task.getDeleteReason());
    }

    private ProcessComment toComment(Comment comment) {
        return new ProcessComment(
                comment.getId(),
                comment.getTaskId(),
                ProcessAction.valueOf(comment.getType()),
                requireUserId(comment.getUserId()),
                comment.getFullMessage(),
                toLocalDateTime(comment.getTime()));
    }

    private List<Long> candidateUserIds(List<? extends IdentityLinkInfo> identityLinks) {
        return identityLinks.stream()
                .filter(link -> "candidate".equals(link.getType()))
                .map(IdentityLinkInfo::getUserId)
                .map(this::parseUserId)
                .filter(value -> value != null)
                .map(value -> (Long) value)
                .distinct()
                .sorted()
                .toList();
    }

    private @Nullable Long parseUserId(@Nullable String value) {
        return value != null && value.matches("[1-9][0-9]*") ? Long.valueOf(value) : null;
    }

    private Long requireUserId(@Nullable String value) {
        Long userId = parseUserId(value);
        if (userId == null) {
            throw new IllegalStateException("Flowable 审批评论缺少有效操作用户");
        }
        return userId;
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value.toInstant().atZone(clock.getZone()).toLocalDateTime();
    }
}
