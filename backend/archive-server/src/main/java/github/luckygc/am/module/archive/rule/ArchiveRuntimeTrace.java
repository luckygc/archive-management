package github.luckygc.am.module.archive.rule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "am_archive_runtime_trace")
public class ArchiveRuntimeTrace implements CreationAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_version_id", nullable = false)
    private Long schemeVersionId;

    @Column(name = "trigger_point", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ArchiveRuntimeTriggerPoint triggerPoint;

    @Column(name = "object_type_code", nullable = false, length = 100)
    private String objectTypeCode;

    @Column(name = "object_id")
    private @Nullable Long objectId;

    @Column(name = "definition_id")
    private @Nullable Long definitionId;

    @Column(name = "definition_code", length = 100)
    private @Nullable String definitionCode;

    @Column(name = "definition_kind", length = 20)
    @Enumerated(EnumType.STRING)
    private @Nullable ArchiveRuntimeDefinitionKind definitionKind;

    @Column(name = "matched_flag", nullable = false)
    private boolean matchedFlag;

    @Column(name = "blocking_flag", nullable = false)
    private boolean blockingFlag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_json", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> actionJson = List.of();

    @Column(length = 1000)
    private @Nullable String message;

    @Column(length = 30)
    @Enumerated(EnumType.STRING)
    private @Nullable ArchiveRuleDecisionSeverity severity;

    @Column(name = "skipped_reason", length = 1000)
    private @Nullable String skippedReason;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
