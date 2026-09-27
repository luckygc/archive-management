package github.luckygc.am.module.approval;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_approval_workflow_definition")
@SoftDelete(columnName = "deleted_flag")
public class ApprovalWorkflowDefinition implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_code", nullable = false, length = 100)
    private String definitionCode;

    @Column(name = "definition_name", nullable = false)
    private String definitionName;

    @Column(name = "business_type", nullable = false, length = 100)
    private String businessType;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "draft_revision", nullable = false)
    private int draftRevision = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph_json", nullable = false, columnDefinition = "jsonb")
    private String graphJson;

    @Column(name = "published_version_id")
    private @Nullable Long publishedVersionId;

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
