package github.luckygc.am.module.archive.item;

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

import github.luckygc.am.common.audit.CreationTimeAuditable;
import github.luckygc.am.common.audit.UpdateTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_item_search_rebuild_job")
public class ArchiveItemSearchProjectionRebuildJob
        implements CreationTimeAuditable, UpdateTimeAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "table_name", nullable = false, length = 63)
    private String tableName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArchiveItemSearchProjectionRebuildJobStatus status =
            ArchiveItemSearchProjectionRebuildJobStatus.QUEUED;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "max_item_id")
    private @Nullable Long maxItemId;

    @Column(name = "last_processed_item_id")
    private @Nullable Long lastProcessedItemId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "error_code", length = 100)
    private @Nullable String errorCode;

    @Column(name = "error_message", length = 1000)
    private @Nullable String errorMessage;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
