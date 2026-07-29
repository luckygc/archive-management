package github.luckygc.am.module.intake;

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

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_intake_package")
public class ArchiveIntakePackage implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_code", length = 100)
    private @Nullable String packageCode;

    @Column(name = "format_profile", nullable = false, length = 50)
    private String formatProfile;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "content_length", nullable = false)
    private long contentLength;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "original_storage_object_id", nullable = false)
    private Long originalStorageObjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArchiveIntakePackageStatus status;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "electronic_file_count", nullable = false)
    private int electronicFileCount;

    @Column(name = "electronic_file_bytes", nullable = false)
    private long electronicFileBytes;

    @Column(name = "validation_passed_count", nullable = false)
    private int validationPassedCount;

    @Column(name = "validation_warning_count", nullable = false)
    private int validationWarningCount;

    @Column(name = "validation_manual_count", nullable = false)
    private int validationManualCount;

    @Column(name = "failure_reason", length = 1000)
    private @Nullable String failureReason;

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Column(name = "reviewed_by")
    private @Nullable Long reviewedBy;

    @Column(name = "review_remark", length = 1000)
    private @Nullable String reviewRemark;

    @Column(name = "source_fixity_confirmed", nullable = false)
    private boolean sourceFixityConfirmed;

    @Column(name = "content_readability_confirmed", nullable = false)
    private boolean contentReadabilityConfirmed;

    @Column(name = "antivirus_passed", nullable = false)
    private boolean antivirusPassed;

    @Column(name = "carrier_safety_confirmed", nullable = false)
    private boolean carrierSafetyConfirmed;

    @Column(name = "handover_completed", nullable = false)
    private boolean handoverCompleted;

    @Column(name = "processing_started_at")
    private @Nullable LocalDateTime processingStartedAt;

    @Column(name = "processing_completed_at")
    private @Nullable LocalDateTime processingCompletedAt;

    @Column(name = "reviewed_at")
    private @Nullable LocalDateTime reviewedAt;

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
