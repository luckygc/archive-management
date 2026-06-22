package github.luckygc.am.module.archive.record;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_physical_object")
public class ArchivePhysicalObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_record_id", nullable = false)
    private Long archiveRecordId;

    @Column(name = "physical_status", nullable = false, length = 50)
    private String physicalStatus = "NONE";

    @Column(name = "box_no", length = 100)
    private String boxNo;

    @Column(name = "location_no", length = 100)
    private String locationNo;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

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
