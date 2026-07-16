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
@Table(name = "am_archive_rule_trace")
public class ArchiveRuleTrace implements CreationAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_version_id", nullable = false)
    private Long schemeVersionId;

    @Column(name = "trigger_code", nullable = false, length = 100)
    private String triggerCode;

    @Column(name = "object_type_code", nullable = false, length = 100)
    private String objectTypeCode;

    @Column(name = "object_id")
    private @Nullable Long objectId;

    @Column(name = "rule_id")
    private @Nullable Long ruleId;

    @Column(name = "rule_code", length = 100)
    private @Nullable String ruleCode;

    @Column(name = "rule_type", length = 50)
    @Enumerated(EnumType.STRING)
    private @Nullable ArchiveRuleType ruleType;

    @Column(name = "matched_flag", nullable = false)
    private boolean matchedFlag;

    @Column(name = "blocking_flag", nullable = false)
    private boolean blockingFlag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "effect_json", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> effectJson = List.of();

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
