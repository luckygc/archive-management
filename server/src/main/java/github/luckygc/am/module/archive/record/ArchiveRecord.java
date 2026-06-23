package github.luckygc.am.module.archive.record;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_record")
public class ArchiveRecord implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_level", nullable = false, length = 30)
    private String archiveLevel;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "fonds_code", nullable = false, length = 100)
    private String fondsCode;

    @Column(name = "fonds_name", nullable = false)
    private String fondsName;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "archive_no", length = 100)
    private String archiveNo;

    @Column(name = "electronic_status", nullable = false, length = 50)
    private String electronicStatus;

    @Column(name = "security_level", length = 50)
    private String securityLevel;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archive_year", nullable = false)
    private int archiveYear;

    @Column(name = "locked_flag", nullable = false)
    private boolean lockedFlag;

    @Column(name = "lock_reason", length = 500)
    private String lockReason;

    @Column(name = "locked_by")
    private Long lockedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
