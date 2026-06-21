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

@Data
@Entity
@Table(name = "am_archive_record_storage_object")
public class ArchiveRecordStorageObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_record_id", nullable = false)
    private Long archiveRecordId;

    @Column(name = "storage_object_id", nullable = false)
    private Long storageObjectId;

    @Column(name = "usage_type", nullable = false, length = 50)
    private String usageType = "DEFAULT";

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;
}
