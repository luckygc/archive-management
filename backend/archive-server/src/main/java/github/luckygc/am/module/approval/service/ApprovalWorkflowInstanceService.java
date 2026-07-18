package github.luckygc.am.module.approval.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.approval.ApprovalAction;
import github.luckygc.am.module.approval.ApprovalInstanceStatus;
import github.luckygc.am.module.approval.ApprovalTaskStatus;
import github.luckygc.am.module.approval.ApprovalWorkflowDefinition;
import github.luckygc.am.module.approval.ApprovalWorkflowDefinitionVersion;
import github.luckygc.am.module.approval.ApprovalWorkflowInstance;
import github.luckygc.am.module.approval._ApprovalWorkflowInstance;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine.ActiveTask;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine.HistoricTask;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine.ProcessAction;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine.ProcessComment;
import github.luckygc.am.module.approval.repository.ApprovalWorkflowInstanceDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.todo.service.UnifiedTodoService;
import github.luckygc.am.module.todo.service.UnifiedTodoService.DispatchUnifiedTodoCommand;
import github.luckygc.am.module.todo.service.UnifiedTodoService.UnifiedTodoItem;

@Service
public class ApprovalWorkflowInstanceService {

    private static final Pattern CONTEXT_KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_CONTEXT_ENTRIES = 100;

    private final ApprovalWorkflowDefinitionService definitionService;
    private final ApprovalWorkflowInstanceDataRepository instanceRepository;
    private final ApprovalProcessEngine processEngine;
    private final UnifiedTodoService todoService;
    private final AuthorizationPermissionService permissionService;
    private final Clock clock;

    public ApprovalWorkflowInstanceService(
            ApprovalWorkflowDefinitionService definitionService,
            ApprovalWorkflowInstanceDataRepository instanceRepository,
            ApprovalProcessEngine processEngine,
            UnifiedTodoService todoService,
            AuthorizationPermissionService permissionService,
            Clock clock) {
        this.definitionService = definitionService;
        this.instanceRepository = instanceRepository;
        this.processEngine = processEngine;
        this.todoService = todoService;
        this.permissionService = permissionService;
        this.clock = clock;
    }

    @Transactional
    public ApprovalWorkflowInstanceResponse startInstance(
            StartApprovalWorkflowInstanceRequest request, Long userId) {
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.APPROVAL_INSTANCE_START);
        ApprovalWorkflowDefinition definition =
                definitionService.loadDefinitionForStart(request.definitionId());
        ApprovalWorkflowDefinitionVersion version =
                definitionService.loadPublishedVersion(definition);
        String businessType = requiredText(request.businessType(), "businessType");
        if (!definition.getBusinessType().equals(businessType)) {
            throw new BadRequestException("业务类型与审批流定义不匹配", "businessType", "业务类型与审批流定义不匹配");
        }
        String businessId = requiredText(request.businessId(), "businessId");
        Map<String, String> businessContext = validateBusinessContext(request.businessContext());
        Map<String, Object> variables = new HashMap<>();
        variables.put("initiatorUserId", userId.toString());
        variables.put("businessType", businessType);
        variables.put("businessId", businessId);
        variables.put("businessContext", businessContext);
        ApprovalProcessEngine.ProcessInstance processInstance =
                processEngine.start(
                        version.getFlowableProcessDefinitionId(),
                        businessType + ":" + businessId,
                        variables);
        ApprovalWorkflowInstance instance = new ApprovalWorkflowInstance();
        instance.setDefinitionId(definition.getId());
        instance.setDefinitionVersionId(version.getId());
        instance.setBusinessType(businessType);
        instance.setBusinessId(businessId);
        instance.setTitle(requiredText(request.title(), "title"));
        instance.setInitiatorUserId(userId);
        instance.setFlowableProcessInstanceId(processInstance.processInstanceId());
        instance = instanceRepository.insert(instance);
        if (syncActiveTask(instance) == null) {
            finishInstance(instance, ApprovalInstanceStatus.APPROVED);
        }
        return toResponse(instance);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ApprovalWorkflowInstanceResponse> listMyStarted(
            @Nullable ApprovalInstanceStatus status, PageRequest pageRequest, Long userId) {
        List<Restriction<ApprovalWorkflowInstance>> restrictions = new ArrayList<>();
        restrictions.add(_ApprovalWorkflowInstance.initiatorUserId.equalTo(userId));
        if (status != null) {
            restrictions.add(_ApprovalWorkflowInstance.status.equalTo(status));
        }
        return CursorPageResponse.from(
                instanceRepository.filterBy(Restrict.all(restrictions), pageRequest),
                pageRequest,
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public ApprovalWorkflowInstanceDetailResponse getInstance(Long id, Long userId) {
        ApprovalWorkflowInstance instance = loadInstance(id);
        return buildDetail(instance, userId);
    }

    @Transactional
    public ApprovalWorkflowInstanceDetailResponse approveTask(
            Long todoId, CompleteApprovalWorkflowTaskRequest request, Long userId) {
        TaskContext context = requireActiveCandidateTask(todoId, userId);
        processEngine.addComment(
                context.task().taskId(),
                context.instance().getFlowableProcessInstanceId(),
                ProcessAction.APPROVE,
                userId,
                request.comment());
        processEngine.completeTask(context.task().taskId(), userId, Map.of("approved", true));
        todoService.completeSource(
                UnifiedTodoService.APPROVAL_SOURCE_TYPE, context.task().taskId(), userId);
        if (syncActiveTask(context.instance()) == null) {
            finishInstance(context.instance(), ApprovalInstanceStatus.APPROVED);
        }
        return buildDetail(context.instance(), userId);
    }

    @Transactional
    public ApprovalWorkflowInstanceDetailResponse rejectTask(
            Long todoId, CompleteApprovalWorkflowTaskRequest request, Long userId) {
        TaskContext context = requireActiveCandidateTask(todoId, userId);
        processEngine.addComment(
                context.task().taskId(),
                context.instance().getFlowableProcessInstanceId(),
                ProcessAction.REJECT,
                userId,
                request.comment());
        processEngine.terminate(context.instance().getFlowableProcessInstanceId(), "审批任务已驳回");
        todoService.completeSource(
                UnifiedTodoService.APPROVAL_SOURCE_TYPE, context.task().taskId(), userId);
        finishInstance(context.instance(), ApprovalInstanceStatus.REJECTED);
        return buildDetail(context.instance(), userId);
    }

    @Transactional
    public ApprovalWorkflowInstanceDetailResponse withdrawInstance(
            Long instanceId, ApprovalWorkflowInstanceActionRequest request, Long userId) {
        ApprovalWorkflowInstance instance = requireRunningInstance(instanceId);
        if (!instance.getInitiatorUserId().equals(userId)) {
            throw new BadRequestException("只有发起人可以撤回审批流程");
        }
        cancelActiveTask(instance);
        processEngine.addComment(
                null,
                instance.getFlowableProcessInstanceId(),
                ProcessAction.WITHDRAW,
                userId,
                request.comment());
        processEngine.terminate(instance.getFlowableProcessInstanceId(), "发起人撤回审批流程");
        finishInstance(instance, ApprovalInstanceStatus.WITHDRAWN);
        return buildDetail(instance, userId);
    }

    @Transactional
    public ApprovalWorkflowInstanceDetailResponse terminateInstance(
            Long instanceId, ApprovalWorkflowInstanceActionRequest request, Long userId) {
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.APPROVAL_INSTANCE_MANAGE);
        ApprovalWorkflowInstance instance = requireRunningInstance(instanceId);
        cancelActiveTask(instance);
        processEngine.addComment(
                null,
                instance.getFlowableProcessInstanceId(),
                ProcessAction.TERMINATE,
                userId,
                request.comment());
        processEngine.terminate(instance.getFlowableProcessInstanceId(), "管理员终止审批流程");
        finishInstance(instance, ApprovalInstanceStatus.TERMINATED);
        return buildDetail(instance, userId);
    }

    private ApprovalWorkflowInstanceDetailResponse buildDetail(
            ApprovalWorkflowInstance instance, Long userId) {
        List<HistoricTask> historicTasks =
                processEngine.findHistoricTasks(instance.getFlowableProcessInstanceId());
        List<ProcessComment> comments =
                processEngine.findComments(instance.getFlowableProcessInstanceId());
        requireView(instance, historicTasks, userId);
        return new ApprovalWorkflowInstanceDetailResponse(
                toResponse(instance),
                historicTasks.stream()
                        .map(task -> toTaskResponse(task, comments, instance.getStatus()))
                        .toList(),
                comments.stream().map(this::toOpinionResponse).toList());
    }

    private @Nullable ActiveTask syncActiveTask(ApprovalWorkflowInstance instance) {
        ActiveTask activeTask =
                processEngine.findActiveTask(instance.getFlowableProcessInstanceId());
        if (activeTask == null) {
            instance.setCurrentNodeCode(null);
            instance.setCurrentNodeName(null);
            instanceRepository.update(instance);
            return null;
        }
        if (activeTask.candidateUserIds().isEmpty()) {
            throw new IllegalStateException("Flowable 审批任务没有候选用户: " + activeTask.nodeCode());
        }
        todoService.dispatch(
                new DispatchUnifiedTodoCommand(
                        UnifiedTodoService.APPROVAL_SOURCE_TYPE,
                        activeTask.taskId(),
                        instance.getBusinessType(),
                        instance.getBusinessId(),
                        instance.getTitle(),
                        activeTask.nodeName(),
                        activeTask.candidateUserIds(),
                        "/approval/center?instanceId=" + instance.getId()));
        instance.setCurrentNodeCode(activeTask.nodeCode());
        instance.setCurrentNodeName(activeTask.nodeName());
        instanceRepository.update(instance);
        return activeTask;
    }

    private TaskContext requireActiveCandidateTask(Long todoId, Long userId) {
        UnifiedTodoItem todo = todoService.requirePending(todoId, userId);
        if (!UnifiedTodoService.APPROVAL_SOURCE_TYPE.equals(todo.sourceType())) {
            throw new BadRequestException("该统一待办不是审批任务");
        }
        ActiveTask activeTask = processEngine.findActiveTaskById(todo.sourceTaskId());
        if (activeTask == null || !activeTask.candidateUserIds().contains(userId)) {
            todoService.cancelTodo(todoId);
            throw new BadRequestException("审批任务已结束或当前用户无权办理");
        }
        ApprovalWorkflowInstance instance =
                instanceRepository.findByFlowableProcessInstanceId(activeTask.processInstanceId());
        if (instance == null || instance.getStatus() != ApprovalInstanceStatus.RUNNING) {
            todoService.cancelTodo(todoId);
            throw new BadRequestException("审批流程实例已经结束");
        }
        return new TaskContext(instance, activeTask);
    }

    private void cancelActiveTask(ApprovalWorkflowInstance instance) {
        ActiveTask task = processEngine.findActiveTask(instance.getFlowableProcessInstanceId());
        if (task != null) {
            todoService.cancelSource(UnifiedTodoService.APPROVAL_SOURCE_TYPE, task.taskId());
        }
    }

    private void finishInstance(ApprovalWorkflowInstance instance, ApprovalInstanceStatus status) {
        instance.setStatus(status);
        instance.setCurrentNodeCode(null);
        instance.setCurrentNodeName(null);
        instance.setCompletedAt(LocalDateTime.now(clock));
        instanceRepository.update(instance);
    }

    private ApprovalWorkflowInstance requireRunningInstance(Long id) {
        ApprovalWorkflowInstance instance = loadInstance(id);
        if (instance.getStatus() != ApprovalInstanceStatus.RUNNING) {
            throw new BadRequestException("审批流程实例已经结束");
        }
        return instance;
    }

    private ApprovalWorkflowInstance loadInstance(Long id) {
        return instanceRepository
                .findById(id)
                .orElseThrow(() -> new BadRequestException("审批流程实例不存在"));
    }

    private void requireView(
            ApprovalWorkflowInstance instance, List<HistoricTask> tasks, Long userId) {
        if (instance.getInitiatorUserId().equals(userId)
                || permissionService.hasPermission(
                        userId, AuthorizationPermissionCode.APPROVAL_INSTANCE_MANAGE.code())) {
            return;
        }
        boolean participant =
                tasks.stream()
                        .anyMatch(
                                task ->
                                        task.candidateUserIds().contains(userId)
                                                || userId.equals(task.assigneeUserId()));
        if (!participant) {
            throw new BadRequestException("当前用户无权查看该审批流程");
        }
    }

    private ApprovalWorkflowInstanceResponse toResponse(ApprovalWorkflowInstance instance) {
        return new ApprovalWorkflowInstanceResponse(
                instance.getId(),
                instance.getDefinitionId(),
                instance.getDefinitionVersionId(),
                instance.getBusinessType(),
                instance.getBusinessId(),
                instance.getTitle(),
                instance.getInitiatorUserId(),
                instance.getStatus(),
                instance.getCurrentNodeCode(),
                instance.getCurrentNodeName(),
                instance.getCreatedAt(),
                instance.getCompletedAt());
    }

    private ApprovalWorkflowTaskResponse toTaskResponse(
            HistoricTask task,
            List<ProcessComment> comments,
            ApprovalInstanceStatus instanceStatus) {
        ProcessAction action =
                comments.stream()
                        .filter(comment -> task.taskId().equals(comment.taskId()))
                        .map(ProcessComment::action)
                        .findFirst()
                        .orElse(null);
        ApprovalTaskStatus status = taskStatus(task, action, instanceStatus);
        return new ApprovalWorkflowTaskResponse(
                task.taskId(),
                task.nodeCode(),
                task.nodeName(),
                status,
                task.candidateUserIds(),
                task.assigneeUserId(),
                task.createdAt(),
                task.completedAt());
    }

    private ApprovalTaskStatus taskStatus(
            HistoricTask task,
            @Nullable ProcessAction action,
            ApprovalInstanceStatus instanceStatus) {
        if (task.completedAt() == null) {
            return ApprovalTaskStatus.PENDING;
        }
        if (action == ProcessAction.REJECT) {
            return ApprovalTaskStatus.REJECTED;
        }
        if (action == ProcessAction.APPROVE) {
            return ApprovalTaskStatus.APPROVED;
        }
        return switch (instanceStatus) {
            case WITHDRAWN -> ApprovalTaskStatus.WITHDRAWN;
            case TERMINATED -> ApprovalTaskStatus.TERMINATED;
            case REJECTED -> ApprovalTaskStatus.REJECTED;
            case RUNNING, APPROVED -> ApprovalTaskStatus.APPROVED;
        };
    }

    private ApprovalWorkflowOpinionResponse toOpinionResponse(ProcessComment comment) {
        return new ApprovalWorkflowOpinionResponse(
                comment.commentId(),
                comment.taskId(),
                ApprovalAction.valueOf(comment.action().name()),
                comment.operatorUserId(),
                comment.message(),
                comment.createdAt());
    }

    private Map<String, String> validateBusinessContext(
            @Nullable Map<String, String> businessContext) {
        if (businessContext == null || businessContext.isEmpty()) {
            return Map.of();
        }
        if (businessContext.size() > MAX_CONTEXT_ENTRIES) {
            throw new BadRequestException("业务上下文字段过多", "businessContext", "最多允许 100 个业务上下文字段");
        }
        Map<String, String> result = new HashMap<>();
        businessContext.forEach(
                (key, value) -> {
                    if (key == null || !CONTEXT_KEY_PATTERN.matcher(key).matches()) {
                        throw new BadRequestException(
                                "业务上下文字段编码不合法", "businessContext", "字段编码必须以小写字母开头且只包含小写字母、数字和下划线");
                    }
                    result.put(key, requiredText(value, "businessContext." + key));
                });
        return Map.copyOf(result);
    }

    private String requiredText(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new BadRequestException("必填字段不能为空", field, "必填字段不能为空");
        }
        return value.trim();
    }

    private record TaskContext(ApprovalWorkflowInstance instance, ActiveTask task) {}

    public record StartApprovalWorkflowInstanceRequest(
            Long definitionId,
            String businessType,
            String businessId,
            String title,
            @Nullable Map<String, String> businessContext) {}

    public record CompleteApprovalWorkflowTaskRequest(@Nullable String comment) {}

    public record ApprovalWorkflowInstanceActionRequest(@Nullable String comment) {}

    public record ApprovalWorkflowInstanceResponse(
            Long id,
            Long definitionId,
            Long definitionVersionId,
            String businessType,
            String businessId,
            String title,
            Long initiatorUserId,
            ApprovalInstanceStatus status,
            @Nullable String currentNodeCode,
            @Nullable String currentNodeName,
            LocalDateTime createdAt,
            @Nullable LocalDateTime completedAt) {}

    public record ApprovalWorkflowTaskResponse(
            String id,
            String nodeCode,
            String nodeName,
            ApprovalTaskStatus status,
            List<Long> candidateUserIds,
            @Nullable Long assigneeUserId,
            LocalDateTime createdAt,
            @Nullable LocalDateTime completedAt) {}

    public record ApprovalWorkflowOpinionResponse(
            String id,
            @Nullable String taskId,
            ApprovalAction action,
            Long operatorUserId,
            @Nullable String comment,
            LocalDateTime createdAt) {}

    public record ApprovalWorkflowInstanceDetailResponse(
            ApprovalWorkflowInstanceResponse instance,
            List<ApprovalWorkflowTaskResponse> tasks,
            List<ApprovalWorkflowOpinionResponse> opinions) {}
}
