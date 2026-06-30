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

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.UpdateTimestamp;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;
import github.luckygc.am.module.archive.ArchiveLevel;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_field")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveField implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "archive_level", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveLevel archiveLevel = ArchiveLevel.ITEM;

    @Column(name = "field_scope", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveFieldScope fieldScope = ArchiveFieldScope.METADATA;

    @Column(name = "field_code", nullable = false, length = 80)
    private String fieldCode;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "field_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveFieldType fieldType;

    @Column(name = "column_name", nullable = false, length = 100)
    private String columnName;

    @Column(name = "text_length")
    private Integer textLength;

    @Column(name = "decimal_precision")
    private Integer decimalPrecision;

    @Column(name = "decimal_scale")
    private Integer decimalScale;

    @Column(name = "edit_control", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveFieldControl editControl = ArchiveFieldControl.INPUT;

    @Column(name = "list_visible", nullable = false)
    private boolean listVisible = true;

    @Column(name = "list_width")
    private Integer listWidth;

    @Column(name = "list_sort_order", nullable = false)
    private int listSortOrder;

    @Column(name = "detail_visible", nullable = false)
    private boolean detailVisible = true;

    @Column(name = "detail_col_span", nullable = false)
    private int detailColSpan = 1;

    @Column(name = "detail_sort_order", nullable = false)
    private int detailSortOrder;

    @Column(name = "edit_visible", nullable = false)
    private boolean editVisible = true;

    @Column(name = "edit_col_span", nullable = false)
    private int editColSpan = 1;

    @Column(name = "edit_sort_order", nullable = false)
    private int editSortOrder;

    @Column(name = "exact_searchable", nullable = false)
    private boolean exactSearchable;

    @Column(name = "data_scope_filterable", nullable = false)
    private boolean dataScopeFilterable;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
