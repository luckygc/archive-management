package github.luckygc.am.module.archive.item;

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
@Table(name = "am_archive_item_audit")
public class ArchiveItemAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_table_name", nullable = false, length = 100)
    private String sourceTableName;

    @Column(name = "source_item_id", nullable = false)
    private Long sourceRecordId;

    @Column(name = "archive_item_id")
    private Long archiveItemId;

    @Column(name = "fonds_code", length = 100)
    private String fondsCode;

    @Column(name = "category_code", length = 100)
    private String categoryCode;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(name = "operation_reason")
    private String operationReason;

    @Column(name = "operated_by")
    private Long operatedBy;

    @Column(name = "operated_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime operatedAt;
}
