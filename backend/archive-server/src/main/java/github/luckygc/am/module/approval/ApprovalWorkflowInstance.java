package github.luckygc.am.module.approval;

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
@Table(name = "am_approval_workflow_instance")
public class ApprovalWorkflowInstance implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "definition_version_id", nullable = false)
    private Long definitionVersionId;

    @Column(name = "business_type", nullable = false, length = 100)
    private String businessType;

    @Column(name = "business_id", nullable = false, length = 200)
    private String businessId;

    @Column(nullable = false)
    private String title;

    @Column(name = "initiator_user_id", nullable = false)
    private Long initiatorUserId;

    @Column(name = "flowable_process_instance_id", nullable = false, length = 100)
    private String flowableProcessInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApprovalInstanceStatus status = ApprovalInstanceStatus.RUNNING;

    @Column(name = "current_node_code", length = 100)
    private @Nullable String currentNodeCode;

    @Column(name = "current_node_name")
    private @Nullable String currentNodeName;

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
