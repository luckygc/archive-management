package github.luckygc.am.module.archive;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_volume_item")
public class ArchiveVolumeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "volume_id", nullable = false)
    private Long volumeId;

    @Column(name = "archive_record_id", nullable = false)
    private Long archiveRecordId;

    @Column(name = "fonds_code", nullable = false, length = 100)
    private String fondsCode;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

}
