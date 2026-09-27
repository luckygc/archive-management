package github.luckygc.am.module.archive.authorization;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationTimeAuditable;
import github.luckygc.am.common.audit.UpdateTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_data_scope")
public class ArchiveDataScope implements CreationTimeAuditable, UpdateTimeAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_code", nullable = false, length = 100)
    private String scopeCode;

    @Column(name = "scope_name", nullable = false, length = 120)
    private String scopeName;

    @Column(name = "scope_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveDataScopeType scopeType = ArchiveDataScopeType.CONDITIONAL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dynamic_condition_json", columnDefinition = "jsonb")
    private @Nullable ArchiveDataScopeDynamicCondition dynamicCondition;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 500)
    private @Nullable String description;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
