package github.luckygc.am.module.archive.metadata;

import java.time.LocalDate;
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
@Table(name = "am_archive_fonds")
public class ArchiveFonds implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fonds_code", nullable = false, length = 100)
    private String fondsCode;

    @Column(name = "fonds_name", nullable = false)
    private String fondsName;

    @Column(name = "fonds_no", length = 100)
    private @Nullable String fondsNo;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveFondsStatus status = ArchiveFondsStatus.ACTIVE;

    @Column(name = "number_assigned_by")
    private @Nullable String numberAssignedBy;

    @Column(name = "number_assigned_at")
    private @Nullable LocalDateTime numberAssignedAt;

    @Column(name = "start_date")
    private @Nullable LocalDate startDate;

    @Column(name = "end_date")
    private @Nullable LocalDate endDate;

    @Column(name = "history_note")
    private @Nullable String historyNote;

    @Column(name = "closed_at")
    private @Nullable LocalDateTime closedAt;

    @Column(name = "closure_reason")
    private @Nullable String closureReason;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
