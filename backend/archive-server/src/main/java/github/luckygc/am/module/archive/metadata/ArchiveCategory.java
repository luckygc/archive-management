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
import jakarta.persistence.Version;

import org.hibernate.annotations.SoftDelete;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_category")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveCategory implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "scheme_id", nullable = false)
    private Long schemeId;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "management_mode", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveManagementMode managementMode = ArchiveManagementMode.ITEM_ONLY;

    @Column(name = "volume_table_name", length = 100)
    private String volumeTableName;

    @Column(name = "item_table_name", length = 100)
    private String itemTableName;

    @Column(name = "volume_physical_table_name", length = 100)
    private String volumePhysicalTableName;

    @Column(name = "item_physical_table_name", length = 100)
    private String itemPhysicalTableName;

    @Column(name = "table_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveTableStatus tableStatus = ArchiveTableStatus.NOT_BUILT;

    @Column(name = "built_at")
    private LocalDateTime builtAt;

    @Column(nullable = false)
    private boolean enabled = true;

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
