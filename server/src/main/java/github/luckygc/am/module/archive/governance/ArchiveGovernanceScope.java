package github.luckygc.am.module.archive.governance;

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

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_governance_scope")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveGovernanceScope implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_version_id", nullable = false)
    private Long schemeVersionId;

    @Column(name = "scope_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveGovernanceScopeType scopeType = ArchiveGovernanceScopeType.GLOBAL;

    @Column(name = "fonds_code", length = 100)
    private @Nullable String fondsCode;

    @Column(name = "category_code", length = 100)
    private @Nullable String categoryCode;

    @Column(name = "default_flag", nullable = false)
    private boolean defaultFlag;

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
