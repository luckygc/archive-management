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

import lombok.Data;

@Data
@Entity
@Table(name = "am_archive_ontology_attribute_type")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveOntologyAttributeType implements CreationAuditable, UpdateAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attribute_code", nullable = false, length = 100)
    private String attributeCode;

    @Column(name = "attribute_name", nullable = false)
    private String attributeName;

    @Column(name = "object_type_id", nullable = false)
    private Long objectTypeId;

    @Column(name = "data_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ArchiveOntologyAttributeDataType dataType;

    @Column(name = "metadata_domain", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ArchiveOntologyMetadataDomain metadataDomain =
            ArchiveOntologyMetadataDomain.DESCRIPTION;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArchiveOntologyCardinality cardinality = ArchiveOntologyCardinality.SINGLE;

    @Column(name = "exact_searchable", nullable = false)
    private boolean exactSearchable;

    @Column(nullable = false)
    private boolean sortable;

    @Column(name = "description_participating", nullable = false)
    private boolean descriptionParticipating;

    @Column(name = "reference_code_participating", nullable = false)
    private boolean referenceCodeParticipating;

    @Column(name = "rule_fact_visible", nullable = false)
    private boolean ruleFactVisible = true;

    @Column(length = 1000)
    private @Nullable String description;

    @Column(nullable = false)
    private boolean enabled = true;

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
