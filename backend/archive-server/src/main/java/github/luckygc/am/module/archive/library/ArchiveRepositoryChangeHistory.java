package github.luckygc.am.module.archive.library;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.module.archive.ArchiveObjectType;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_repository_change_history")
public class ArchiveRepositoryChangeHistory implements CreationAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "archive_type", nullable = false, length = 20)
    private ArchiveObjectType archiveType;

    @Column(name = "archive_id", nullable = false)
    private Long archiveId;

    @Column(name = "from_repository_id")
    private @Nullable Long fromRepositoryId;

    @Column(name = "to_repository_id", nullable = false)
    private Long toRepositoryId;

    @Column(name = "business_type", length = 80)
    private @Nullable String businessType;

    @Column(name = "business_id")
    private @Nullable Long businessId;

    @Column(length = 500)
    private @Nullable String reason;

    @Column(name = "operated_by")
    private @Nullable Long operatedBy;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
