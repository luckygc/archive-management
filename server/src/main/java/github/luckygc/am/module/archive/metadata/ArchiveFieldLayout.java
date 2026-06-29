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

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_field_layout")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveFieldLayout implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ArchiveLayoutSurface surface;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "list_width")
    private Integer listWidth;

    @Column(name = "col_span", nullable = false)
    private int colSpan = 1;

    @Column(name = "row_order", nullable = false)
    private int rowOrder;

    @Column(name = "col_order", nullable = false)
    private int colOrder;

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
