package github.luckygc.am.module.archive.metadata;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_category")
public class ArchiveCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "management_mode", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveManagementMode managementMode = ArchiveManagementMode.item_only;

    @Column(name = "volume_table_name", length = 100)
    private String volumeTableName;

    @Column(name = "item_table_name", length = 100)
    private String itemTableName;

    @Column(name = "table_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveTableStatus tableStatus = ArchiveTableStatus.not_built;

    @Column(name = "built_at")
    private LocalDateTime builtAt;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
