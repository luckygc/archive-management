package github.luckygc.am.module.archive.ontology;

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
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.audit.CreationAuditable;
import github.luckygc.am.common.audit.UpdateAuditable;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_ontology_attribute_mapping")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveOntologyAttributeMapping implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attribute_type_id", nullable = false)
    private Long attributeTypeId;

    @Column(name = "mapping_kind", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ArchiveOntologyAttributeMappingKind mappingKind;

    @Column(name = "fixed_field_code", length = 100)
    private @Nullable String fixedFieldCode;

    @Column(name = "category_id")
    private @Nullable Long categoryId;

    @Column(name = "archive_level", length = 30)
    @Enumerated(EnumType.STRING)
    private @Nullable ArchiveLevel archiveLevel;

    @Column(name = "field_scope", length = 30)
    @Enumerated(EnumType.STRING)
    private @Nullable ArchiveFieldScope fieldScope;

    @Column(name = "dynamic_field_id")
    private @Nullable Long dynamicFieldId;

    @Column(name = "line_table_id")
    private @Nullable Long lineTableId;

    @Column(name = "line_field_id")
    private @Nullable Long lineFieldId;

    @Column(name = "component_field_code", length = 100)
    private @Nullable String componentFieldCode;

    @Column(name = "process_field_code", length = 100)
    private @Nullable String processFieldCode;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private @Nullable Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
