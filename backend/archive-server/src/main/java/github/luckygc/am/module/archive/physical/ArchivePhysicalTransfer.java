package github.luckygc.am.module.archive.physical;

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
@Table(name = "am_archive_physical_transfer")
public class ArchivePhysicalTransfer implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_no", nullable = false, length = 80)
    private String transferNo;

    @Column(name = "source_department_id", nullable = false)
    private Long sourceDepartmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ArchivePhysicalTransferStatus status;

    @Column(length = 1000)
    private @Nullable String remark;

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "received_by")
    private @Nullable Long receivedBy;

    @Column(name = "received_at")
    private @Nullable LocalDateTime receivedAt;

    @Column(name = "receipt_note", length = 1000)
    private @Nullable String receiptNote;

    @Column(name = "rejected_by")
    private @Nullable Long rejectedBy;

    @Column(name = "rejected_at")
    private @Nullable LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 1000)
    private @Nullable String rejectionReason;

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
