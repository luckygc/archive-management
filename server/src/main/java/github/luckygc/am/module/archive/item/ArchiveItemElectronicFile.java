package github.luckygc.am.module.archive.item;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;

import github.luckygc.am.common.audit.CreationAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_item_electronic_file")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveItemElectronicFile implements CreationAuditable {

    @Id
    @GeneratedValue(generator = "am_archive_item_electronic_file_id_seq")
    @SequenceGenerator(
            name = "am_archive_item_electronic_file_id_seq",
            sequenceName = "am_archive_item_electronic_file_id_seq",
            allocationSize = 1000)
    private Long id;

    @Column(name = "archive_item_id", nullable = false)
    private Long archiveItemId;

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
}
