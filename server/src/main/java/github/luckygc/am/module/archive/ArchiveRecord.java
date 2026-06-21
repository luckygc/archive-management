package github.luckygc.am.module.archive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Entity
@Table(name = "am_archive_record")
public class ArchiveRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fonds_code", nullable = false, length = 100)
    private String fondsCode;

    @Column(name = "category_group_code", length = 100)
    private String categoryGroupCode;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "archive_status", nullable = false, length = 50)
    private String archiveStatus;

    @Column(name = "process_status", nullable = false, length = 50)
    private String processStatus;

    @Column(name = "security_level", length = 50)
    private String securityLevel;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "organization_code", length = 100)
    private String organizationCode;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Column(name = "archive_year", nullable = false)
    private int archiveYear;

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
