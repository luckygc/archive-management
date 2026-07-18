package github.luckygc.am.module.archive.authorization;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationTimeAuditable;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_data_scope_dimension")
public class ArchiveDataScopeDimension implements CreationTimeAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    @Column(name = "dimension_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveDataScopeDimensionType dimensionType;

    @Column(name = "target_id")
    private @Nullable Long targetId;

    @Column(name = "target_code", length = 120)
    private @Nullable String targetCode;

    @Column(name = "include_descendants", nullable = false)
    private boolean includeDescendants;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
