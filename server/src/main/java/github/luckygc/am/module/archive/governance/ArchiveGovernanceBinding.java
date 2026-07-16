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
@Table(name = "am_archive_governance_binding")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveGovernanceBinding implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_version_id", nullable = false)
    private Long schemeVersionId;

    @Column(name = "binding_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ArchiveGovernanceBindingType bindingType;

    @Column(name = "target_type", length = 100)
    private @Nullable String targetType;

    @Column(name = "target_id")
    private @Nullable Long targetId;

    @Column(name = "target_code", length = 100)
    private @Nullable String targetCode;

    @Column(name = "binding_order", nullable = false)
    private int bindingOrder;

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
