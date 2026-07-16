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

import org.hibernate.annotations.SoftDelete;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_governance_scheme_version")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveGovernanceSchemeVersion implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_id", nullable = false)
    private Long schemeId;

    @Column(name = "version_code", nullable = false, length = 100)
    private String versionCode;

    @Column(name = "version_description", length = 1000)
    private @Nullable String versionDescription;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveGovernanceSchemeVersionStatus status =
            ArchiveGovernanceSchemeVersionStatus.DRAFT;

    @Column(name = "published_by")
    private @Nullable Long publishedBy;

    @Column(name = "published_at")
    private @Nullable LocalDateTime publishedAt;

    @Column(name = "frozen_by")
    private @Nullable Long frozenBy;

    @Column(name = "frozen_at")
    private @Nullable LocalDateTime frozenAt;

    @Column(name = "retired_by")
    private @Nullable Long retiredBy;

    @Column(name = "retired_at")
    private @Nullable LocalDateTime retiredAt;

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
