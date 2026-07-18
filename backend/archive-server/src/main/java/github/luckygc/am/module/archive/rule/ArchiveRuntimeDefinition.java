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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;
import github.luckygc.am.module.archive.ArchiveLevel;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_runtime_definition")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveRuntimeDefinition implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_version_id", nullable = false)
    private Long schemeVersionId;

    @Column(name = "definition_kind", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ArchiveRuntimeDefinitionKind definitionKind;

    @Column(name = "definition_code", nullable = false, length = 100)
    private String definitionCode;

    @Column(name = "definition_name", nullable = false)
    private String definitionName;

    @Column(name = "trigger_point", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ArchiveRuntimeTriggerPoint triggerPoint;

    @Column(name = "scope_fonds_code", length = 100)
    private @Nullable String scopeFondsCode;

    @Column(name = "scope_category_code", length = 100)
    private @Nullable String scopeCategoryCode;

    @Column(name = "scope_archive_level", length = 30)
    @Enumerated(EnumType.STRING)
    private @Nullable ArchiveLevel scopeArchiveLevel;

    @Column(nullable = false)
    private int priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> conditionJson = Map.of();

    @Column(name = "constraint_action", length = 20)
    @Enumerated(EnumType.STRING)
    private @Nullable ArchiveRuntimeActionType constraintAction;

    @Column(name = "constraint_message", length = 1000)
    private @Nullable String constraintMessage;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveRuntimeStatus status = ArchiveRuntimeStatus.DRAFT;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "field_catalog_signature", length = 64)
    private @Nullable String fieldCatalogSignature;

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
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private @Nullable Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
