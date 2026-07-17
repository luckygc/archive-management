package github.luckygc.am.module.approval;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_approval_workflow_definition_version")
public class ApprovalWorkflowDefinitionVersion implements CreationAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph_json", nullable = false, columnDefinition = "jsonb")
    private String graphJson;

    @Column(name = "flowable_deployment_id", nullable = false, length = 100)
    private String flowableDeploymentId;

    @Column(name = "flowable_process_definition_id", nullable = false, length = 150)
    private String flowableProcessDefinitionId;

    @Column(name = "flowable_process_definition_key", nullable = false, length = 100)
    private String flowableProcessDefinitionKey;

    @Column(name = "published_by", nullable = false)
    private Long publishedBy;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
