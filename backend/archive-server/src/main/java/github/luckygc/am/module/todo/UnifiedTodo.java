package github.luckygc.am.module.todo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_unified_todo")
public class UnifiedTodo implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 100)
    private String sourceType;

    @Column(name = "source_task_id", nullable = false, length = 150)
    private String sourceTaskId;

    @Column(name = "business_type", nullable = false, length = 100)
    private String businessType;

    @Column(name = "business_id", nullable = false, length = 200)
    private String businessId;

    @Column(nullable = false)
    private String title;

    @Column(name = "node_name")
    private @Nullable String nodeName;

    @Column(name = "assignee_user_id", nullable = false)
    private Long assigneeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UnifiedTodoStatus status = UnifiedTodoStatus.PENDING;

    @Column(name = "source_path", nullable = false, length = 500)
    private String sourcePath;

    @Column(name = "completed_at")
    private @Nullable LocalDateTime completedAt;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private @Nullable Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
