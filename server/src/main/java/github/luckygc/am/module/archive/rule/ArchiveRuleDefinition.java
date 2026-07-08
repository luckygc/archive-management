package github.luckygc.am.module.archive.rule;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;
import github.luckygc.am.module.archive.ArchiveLevel;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_rule_definition")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveRuleDefinition implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_version_id", nullable = false)
    private Long schemeVersionId;

    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "rule_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ArchiveRuleType ruleType;

    @Column(name = "trigger_code", nullable = false, length = 100)
    private String triggerCode;

    @Column(name = "scope_fonds_code", length = 100)
    private @Nullable String scopeFondsCode;

    @Column(name = "scope_category_code", length = 100)
    private @Nullable String scopeCategoryCode;

    @Column(name = "scope_object_type_id")
    private @Nullable Long scopeObjectTypeId;

    @Column(name = "scope_archive_level", length = 30)
    @Enumerated(EnumType.STRING)
    private @Nullable ArchiveLevel scopeArchiveLevel;

    @Column(name = "scope_event_type_id")
    private @Nullable Long scopeEventTypeId;

    @Column(nullable = false)
    private int priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> conditionJson = Map.of();

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveRuleStatus status = ArchiveRuleStatus.DRAFT;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "published_by")
    private @Nullable Long publishedBy;

    @Column(name = "published_at")
    private @Nullable LocalDateTime publishedAt;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private @Nullable Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
