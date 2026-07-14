import type { ArchiveFieldScope, ArchiveLevel } from "./archive-metadata";

export type ArchiveOntologyAttributeDataType =
    | "TEXT"
    | "INTEGER"
    | "DECIMAL"
    | "DATE"
    | "DATETIME"
    | "BOOLEAN"
    | "ENUM"
    | "REFERENCE"
    | "AMOUNT"
    | "ORGANIZATION"
    | "PERSON";
export type ArchiveOntologyMetadataDomain =
    | "DESCRIPTION"
    | "STRUCTURE"
    | "MANAGEMENT"
    | "TECHNICAL"
    | "ACCESS_USE"
    | "PRESERVATION";
export type ArchiveOntologyCardinality = "SINGLE" | "MULTI" | "REPEATED_ROW";
export type ArchiveOntologyAttributeMappingKind =
    | "FIXED_FIELD"
    | "DYNAMIC_FIELD"
    | "LINE_FIELD"
    | "FILE_COMPONENT_FIELD"
    | "PROCESS_FIELD";
export type ArchiveOntologyRelationDirection = "ONE_WAY" | "TWO_WAY" | "HIERARCHICAL";
export type ArchiveOntologyRelationCardinality =
    | "ONE_TO_ONE"
    | "ONE_TO_MANY"
    | "MANY_TO_ONE"
    | "MANY_TO_MANY";

export interface ArchiveOntologyObjectTypeDto {
    id: number;
    typeCode: string;
    typeName: string;
    description?: string;
    builtin: boolean;
    enabled: boolean;
}

export interface ArchiveOntologyObjectTypeRequest {
    typeCode: string;
    typeName: string;
    description?: string;
    enabled?: boolean;
}

export interface ArchiveOntologyAttributeTypeDto {
    id: number;
    attributeCode: string;
    attributeName: string;
    objectTypeId: number;
    dataType: ArchiveOntologyAttributeDataType;
    metadataDomain: ArchiveOntologyMetadataDomain;
    cardinality: ArchiveOntologyCardinality;
    exactSearchable: boolean;
    sortable: boolean;
    descriptionParticipating: boolean;
    referenceCodeParticipating: boolean;
    ruleFactVisible: boolean;
    description?: string;
    enabled: boolean;
}

export interface ArchiveOntologyAttributeTypeRequest {
    attributeCode: string;
    attributeName: string;
    objectTypeId: number;
    dataType: ArchiveOntologyAttributeDataType;
    metadataDomain: ArchiveOntologyMetadataDomain;
    cardinality?: ArchiveOntologyCardinality;
    exactSearchable?: boolean;
    sortable?: boolean;
    descriptionParticipating?: boolean;
    referenceCodeParticipating?: boolean;
    ruleFactVisible?: boolean;
    description?: string;
    enabled?: boolean;
}

export interface ArchiveOntologyAttributeMappingRequest {
    attributeTypeId: number;
    mappingKind: ArchiveOntologyAttributeMappingKind;
    fixedFieldCode?: string;
    categoryId?: number;
    archiveLevel?: ArchiveLevel;
    fieldScope?: ArchiveFieldScope;
    dynamicFieldId?: number;
    lineTableId?: number;
    lineFieldId?: number;
    componentFieldCode?: string;
    processFieldCode?: string;
}

export interface ArchiveOntologyAttributeMappingDto extends ArchiveOntologyAttributeMappingRequest {
    id: number;
}

export interface ArchiveOntologyRelationTypeDto {
    id: number;
    relationCode: string;
    relationName: string;
    sourceObjectTypeId: number;
    targetObjectTypeId: number;
    relationDirection: ArchiveOntologyRelationDirection;
    cardinality: ArchiveOntologyRelationCardinality;
    description?: string;
    enabled: boolean;
}

export interface ArchiveOntologyRelationTypeRequest {
    relationCode: string;
    relationName: string;
    sourceObjectTypeId: number;
    targetObjectTypeId: number;
    relationDirection: ArchiveOntologyRelationDirection;
    cardinality?: ArchiveOntologyRelationCardinality;
    description?: string;
    enabled?: boolean;
}

export interface ArchiveOntologyEventTypeDto {
    id: number;
    eventCode: string;
    eventName: string;
    objectTypeId: number;
    description?: string;
    enabled: boolean;
}

export interface ArchiveOntologyEventTypeRequest {
    eventCode: string;
    eventName: string;
    objectTypeId: number;
    description?: string;
    enabled?: boolean;
}
