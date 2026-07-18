package github.luckygc.am.module.todo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.data.page.PageRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.todo.UnifiedTodo;
import github.luckygc.am.module.todo.UnifiedTodoStatus;
import github.luckygc.am.module.todo.repository.UnifiedTodoDataRepository;

@Service
public class UnifiedTodoService {

    public static final String APPROVAL_SOURCE_TYPE = "APPROVAL_WORKFLOW";

    private final UnifiedTodoDataRepository repository;
    private final Clock clock;

    public UnifiedTodoService(UnifiedTodoDataRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public List<UnifiedTodoItem> dispatch(DispatchUnifiedTodoCommand command) {
        String sourceType = requiredCode(command.sourceType(), "sourceType");
        String sourceTaskId = requiredText(command.sourceTaskId(), "sourceTaskId");
        String sourcePath = validateSourcePath(command.sourcePath());
        List<Long> assigneeUserIds =
                command.assigneeUserIds() == null
                        ? List.of()
                        : command.assigneeUserIds().stream().distinct().sorted().toList();
        if (assigneeUserIds.isEmpty()
                || assigneeUserIds.stream().anyMatch(userId -> userId == null || userId <= 0)) {
            throw new BadRequestException("统一待办办理人不能为空", "assigneeUserIds", "办理人必须是去重后的正整数 ID");
        }
        return assigneeUserIds.stream()
                .map(
                        assigneeUserId ->
                                dispatchOne(
                                        command,
                                        sourceType,
                                        sourceTaskId,
                                        sourcePath,
                                        assigneeUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<UnifiedTodoItem> listMy(
            boolean completed, PageRequest pageRequest, Long userId) {
        UnifiedTodoStatus status =
                completed ? UnifiedTodoStatus.COMPLETED : UnifiedTodoStatus.PENDING;
        return CursorPageResponse.from(
                repository.findForAssignee(userId, status, pageRequest), pageRequest, this::toItem);
    }

    @Transactional(readOnly = true)
    public UnifiedTodoItem requirePending(Long todoId, Long userId) {
        UnifiedTodo todo =
                repository.findById(todoId).orElseThrow(() -> new BadRequestException("统一待办不存在"));
        if (!todo.getAssigneeUserId().equals(userId)) {
            throw new BadRequestException("当前用户无权办理该待办");
        }
        if (todo.getStatus() != UnifiedTodoStatus.PENDING) {
            throw new BadRequestException("统一待办已失效或已办理");
        }
        return toItem(todo);
    }

    @Transactional
    public void completeSource(String sourceType, String sourceTaskId, Long completedBy) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (UnifiedTodo todo :
                repository.findBySourceTypeAndSourceTaskId(sourceType, sourceTaskId)) {
            if (todo.getStatus() != UnifiedTodoStatus.PENDING) {
                continue;
            }
            todo.setStatus(
                    todo.getAssigneeUserId().equals(completedBy)
                            ? UnifiedTodoStatus.COMPLETED
                            : UnifiedTodoStatus.CANCELLED);
            todo.setCompletedAt(now);
            repository.update(todo);
        }
    }

    @Transactional
    public void cancelSource(String sourceType, String sourceTaskId) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (UnifiedTodo todo :
                repository.findBySourceTypeAndSourceTaskId(sourceType, sourceTaskId)) {
            if (todo.getStatus() == UnifiedTodoStatus.PENDING) {
                todo.setStatus(UnifiedTodoStatus.CANCELLED);
                todo.setCompletedAt(now);
                repository.update(todo);
            }
        }
    }

    @Transactional
    public void cancelTodo(Long todoId) {
        UnifiedTodo todo = repository.findById(todoId).orElse(null);
        if (todo != null && todo.getStatus() == UnifiedTodoStatus.PENDING) {
            todo.setStatus(UnifiedTodoStatus.CANCELLED);
            todo.setCompletedAt(LocalDateTime.now(clock));
            repository.update(todo);
        }
    }

    private UnifiedTodoItem dispatchOne(
            DispatchUnifiedTodoCommand command,
            String sourceType,
            String sourceTaskId,
            String sourcePath,
            Long assigneeUserId) {
        UnifiedTodo existing =
                repository.findBySourceTypeAndSourceTaskIdAndAssigneeUserId(
                        sourceType, sourceTaskId, assigneeUserId);
        if (existing != null) {
            return toItem(existing);
        }
        UnifiedTodo todo = new UnifiedTodo();
        todo.setSourceType(sourceType);
        todo.setSourceTaskId(sourceTaskId);
        todo.setBusinessType(requiredCode(command.businessType(), "businessType"));
        todo.setBusinessId(requiredText(command.businessId(), "businessId"));
        todo.setTitle(requiredText(command.title(), "title"));
        todo.setNodeName(normalizeNullable(command.nodeName()));
        todo.setAssigneeUserId(assigneeUserId);
        todo.setSourcePath(sourcePath);
        return toItem(repository.insert(todo));
    }

    private String validateSourcePath(String value) {
        String path = requiredText(value, "sourcePath");
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("://")) {
            throw new BadRequestException("统一待办来源路径非法", "sourcePath", "来源路径必须是以单个 / 开头的站内绝对路径");
        }
        return path;
    }

    private String requiredCode(String value, String field) {
        String code = requiredText(value, field);
        if (!code.matches("[A-Za-z][A-Za-z0-9_-]{0,99}")) {
            throw new BadRequestException("编码格式不正确", field, "编码只能包含字母、数字、下划线或连字符");
        }
        return code;
    }

    private String requiredText(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new BadRequestException("必填字段不能为空", field, "必填字段不能为空");
        }
        return value.trim();
    }

    private @Nullable String normalizeNullable(@Nullable String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private UnifiedTodoItem toItem(UnifiedTodo todo) {
        return new UnifiedTodoItem(
                todo.getId(),
                todo.getSourceType(),
                todo.getSourceTaskId(),
                todo.getBusinessType(),
                todo.getBusinessId(),
                todo.getTitle(),
                todo.getNodeName(),
                todo.getAssigneeUserId(),
                todo.getStatus(),
                todo.getSourcePath(),
                todo.getCreatedAt(),
                todo.getCompletedAt());
    }

    public record DispatchUnifiedTodoCommand(
            String sourceType,
            String sourceTaskId,
            String businessType,
            String businessId,
            String title,
            @Nullable String nodeName,
            List<Long> assigneeUserIds,
            String sourcePath) {}

    public record UnifiedTodoItem(
            Long id,
            String sourceType,
            String sourceTaskId,
            String businessType,
            String businessId,
            String title,
            @Nullable String nodeName,
            Long assigneeUserId,
            UnifiedTodoStatus status,
            String sourcePath,
            LocalDateTime createdAt,
            @Nullable LocalDateTime completedAt) {}
}
