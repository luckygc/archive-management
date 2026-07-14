package github.luckygc.am.module.archive.ontology.service;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeDataType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeMappingKind;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyCardinality;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyMetadataDomain;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyRelationCardinality;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyRelationDirection;

public abstract class ArchiveOntologyTypes {

    protected ArchiveOntologyTypes() {}

    public interface AttributeTypeRequest {
        String attributeCode();

        String attributeName();

        Long objectTypeId();

        ArchiveOntologyAttributeDataType dataType();

        ArchiveOntologyMetadataDomain metadataDomain();

        @Nullable ArchiveOntologyCardinality cardinality();

        @Nullable Boolean exactSearchable();

        @Nullable Boolean sortable();

        @Nullable Boolean descriptionParticipating();

        @Nullable Boolean referenceCodeParticipating();

        @Nullable Boolean ruleFactVisible();

        @Nullable String description();

        @Nullable Boolean enabled();
    }

    public record CreateArchiveOntologyObjectTypeRequest(
            String typeCode,
            String typeName,
            @Nullable String description,
            @Nullable Boolean enabled) {}

    public record UpdateArchiveOntologyObjectTypeRequest(
            String typeCode,
            String typeName,
            @Nullable String description,
            @Nullable Boolean enabled) {}

    public record ArchiveOntologyObjectTypeResponse(
            Long id,
            String typeCode,
            String typeName,
            @Nullable String description,
            boolean builtin,
            boolean enabled) {}

    public record CreateArchiveOntologyAttributeTypeRequest(
            String attributeCode,
            String attributeName,
            Long objectTypeId,
            ArchiveOntologyAttributeDataType dataType,
            ArchiveOntologyMetadataDomain metadataDomain,
            @Nullable ArchiveOntologyCardinality cardinality,
            @Nullable Boolean exactSearchable,
            @Nullable Boolean sortable,
            @Nullable Boolean descriptionParticipating,
            @Nullable Boolean referenceCodeParticipating,
            @Nullable Boolean ruleFactVisible,
            @Nullable String description,
            @Nullable Boolean enabled)
            implements AttributeTypeRequest {}

    public record UpdateArchiveOntologyAttributeTypeRequest(
            String attributeCode,
            String attributeName,
            Long objectTypeId,
            ArchiveOntologyAttributeDataType dataType,
            ArchiveOntologyMetadataDomain metadataDomain,
            @Nullable ArchiveOntologyCardinality cardinality,
            @Nullable Boolean exactSearchable,
            @Nullable Boolean sortable,
            @Nullable Boolean descriptionParticipating,
            @Nullable Boolean referenceCodeParticipating,
            @Nullable Boolean ruleFactVisible,
            @Nullable String description,
            @Nullable Boolean enabled)
            implements AttributeTypeRequest {}

    public record ArchiveOntologyAttributeTypeResponse(
            Long id,
            String attributeCode,
            String attributeName,
            Long objectTypeId,
            ArchiveOntologyAttributeDataType dataType,
            ArchiveOntologyMetadataDomain metadataDomain,
            ArchiveOntologyCardinality cardinality,
            boolean exactSearchable,
            boolean sortable,
            boolean descriptionParticipating,
            boolean referenceCodeParticipating,
            boolean ruleFactVisible,
            @Nullable String description,
            boolean enabled) {}

    public record CreateArchiveOntologyAttributeMappingRequest(
            Long attributeTypeId,
            ArchiveOntologyAttributeMappingKind mappingKind,
            @Nullable String fixedFieldCode,
            @Nullable Long categoryId,
            @Nullable ArchiveLevel archiveLevel,
            @Nullable ArchiveFieldScope fieldScope,
            @Nullable Long dynamicFieldId,
            @Nullable Long lineTableId,
            @Nullable Long lineFieldId,
            @Nullable String componentFieldCode,
            @Nullable String processFieldCode) {}

    public record ArchiveOntologyAttributeMappingResponse(
            Long id,
            Long attributeTypeId,
            ArchiveOntologyAttributeMappingKind mappingKind,
            @Nullable String fixedFieldCode,
            @Nullable Long categoryId,
            @Nullable ArchiveLevel archiveLevel,
            @Nullable ArchiveFieldScope fieldScope,
            @Nullable Long dynamicFieldId,
            @Nullable Long lineTableId,
            @Nullable Long lineFieldId,
            @Nullable String componentFieldCode,
            @Nullable String processFieldCode) {}

    public record CreateArchiveOntologyRelationTypeRequest(
            String relationCode,
            String relationName,
            Long sourceObjectTypeId,
            Long targetObjectTypeId,
            ArchiveOntologyRelationDirection relationDirection,
            @Nullable ArchiveOntologyRelationCardinality cardinality,
            @Nullable String description,
            @Nullable Boolean enabled) {}

    public record UpdateArchiveOntologyRelationTypeRequest(
            String relationCode,
            String relationName,
            Long sourceObjectTypeId,
            Long targetObjectTypeId,
            ArchiveOntologyRelationDirection relationDirection,
            @Nullable ArchiveOntologyRelationCardinality cardinality,
            @Nullable String description,
            @Nullable Boolean enabled) {}

    public record ArchiveOntologyRelationTypeResponse(
            Long id,
            String relationCode,
            String relationName,
            Long sourceObjectTypeId,
            Long targetObjectTypeId,
            ArchiveOntologyRelationDirection relationDirection,
            ArchiveOntologyRelationCardinality cardinality,
            @Nullable String description,
            boolean enabled) {}

    public record CreateArchiveOntologyEventTypeRequest(
            String eventCode,
            String eventName,
            Long objectTypeId,
            @Nullable String description,
            @Nullable Boolean enabled) {}

    public record UpdateArchiveOntologyEventTypeRequest(
            String eventCode,
            String eventName,
            Long objectTypeId,
            @Nullable String description,
            @Nullable Boolean enabled) {}

    public record ArchiveOntologyEventTypeResponse(
            Long id,
            String eventCode,
            String eventName,
            Long objectTypeId,
            @Nullable String description,
            boolean enabled) {}
}
