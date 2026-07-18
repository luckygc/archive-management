import type { ArchiveLevel } from "./archive-metadata";

export type ArchiveRuntimeDefinitionKind = "CONSTRAINT" | "RULE";
export type ArchiveRuntimeStatus = "DRAFT" | "PUBLISHED";
export type ArchiveRuntimeActionType = "REJECT" | "WARN" | "SET_FIELD";
export type ArchiveRuntimeTriggerPoint =
    | "ITEM_BEFORE_CREATE"
    | "ITEM_BEFORE_UPDATE"
    | "ITEM_BEFORE_DELETE"
    | "VOLUME_BEFORE_CREATE"
    | "VOLUME_BEFORE_ADD_ITEM"
    | "FILE_BEFORE_UPLOAD"
    | "EXPORT_BEFORE_CREATE";
export type ArchiveRuntimeFieldDataType =
    | "TEXT"
    | "INTEGER"
    | "DECIMAL"
    | "DATE"
    | "DATETIME"
    | "BOOLEAN"
    | "ENUM"
    | "REFERENCE";
export type ArchiveRuntimeFieldSource =
    | "FIXED"
    | "METADATA"
    | "PHYSICAL"
    | "CONTEXT"
    | "FILE"
    | "EXPORT";
export type ArchiveRuntimeDecisionSeverity = "INFO" | "WARNING" | "ERROR";

export interface ArchiveRuntimeActionRequest {
    actionType: ArchiveRuntimeActionType;
    actionOrder?: number;
    actionParams?: Record<string, unknown>;
}

export interface ArchiveRuntimeDefinitionRequest {
    schemeVersionId: number;
    definitionKind: ArchiveRuntimeDefinitionKind;
    definitionCode: string;
    definitionName: string;
    triggerPoint: ArchiveRuntimeTriggerPoint;
    scopeFondsCode?: string;
    scopeCategoryCode?: string;
    scopeArchiveLevel?: ArchiveLevel;
    priority?: number;
    conditionJson: Record<string, unknown>;
    constraintAction?: "REJECT" | "WARN";
    constraintMessage?: string;
    enabled?: boolean;
    actions?: ArchiveRuntimeActionRequest[];
}

export interface ArchiveRuntimeActionDto {
    id: number;
    actionType: ArchiveRuntimeActionType;
    actionOrder: number;
    actionParams: Record<string, unknown>;
}

export interface ArchiveRuntimeDefinitionDto extends ArchiveRuntimeDefinitionRequest {
    id: number;
    status: ArchiveRuntimeStatus;
    enabled: boolean;
    priority: number;
    fieldCatalogSignature?: string;
    actions: ArchiveRuntimeActionDto[];
}

export interface ArchiveRuntimeFieldDto {
    fieldCode: string;
    fieldName: string;
    dataType: ArchiveRuntimeFieldDataType;
    source: ArchiveRuntimeFieldSource;
    readable: boolean;
    writable: boolean;
    categoryCode?: string;
}

export interface ArchiveRuntimeFieldCatalogDto {
    schemeVersionId: number;
    categoryCode?: string;
    triggerPoint: ArchiveRuntimeTriggerPoint;
    signature: string;
    fields: ArchiveRuntimeFieldDto[];
}

export interface ArchiveRuntimeExecutionRequest {
    schemeVersionId: number;
    triggerPoint: ArchiveRuntimeTriggerPoint;
    fondsCode?: string;
    categoryCode?: string;
    archiveLevel?: ArchiveLevel;
    objectTypeCode?: string;
    objectId?: number;
    candidateFacts: Record<string, unknown>;
}

export interface ArchiveRuntimeActionDecision {
    actionType: ArchiveRuntimeActionType;
    params: Record<string, unknown>;
}

export interface ArchiveRuntimeDecisionDto {
    definitionId?: number;
    definitionCode: string;
    definitionKind: ArchiveRuntimeDefinitionKind;
    matched: boolean;
    actions: ArchiveRuntimeActionDecision[];
    message?: string;
    severity: ArchiveRuntimeDecisionSeverity;
    blocking: boolean;
    skippedReason?: string;
}

export interface ArchiveRuntimeWarningDto {
    definitionCode: string;
    message?: string;
}

export interface ArchiveRuntimeExecutionResult {
    candidateFacts: Record<string, unknown>;
    assignments: Record<string, unknown>;
    decisions: ArchiveRuntimeDecisionDto[];
    warnings: ArchiveRuntimeWarningDto[];
    blocking: boolean;
}

export interface SearchArchiveRuntimeTracesRequest {
    schemeVersionId?: number;
    triggerPoint?: ArchiveRuntimeTriggerPoint;
    objectTypeCode?: string;
    objectId?: number;
    definitionKind?: ArchiveRuntimeDefinitionKind;
}

export interface SearchArchiveRuntimeTracesQuery extends SearchArchiveRuntimeTracesRequest {
    limit?: number;
    cursor?: string;
    requestTotal?: boolean;
}

export interface ArchiveRuntimeTraceDto {
    id: number;
    schemeVersionId: number;
    triggerPoint: ArchiveRuntimeTriggerPoint;
    objectTypeCode: string;
    objectId?: number;
    definitionId?: number;
    definitionCode?: string;
    definitionKind?: ArchiveRuntimeDefinitionKind;
    matchedFlag: boolean;
    blockingFlag: boolean;
    actionJson: unknown;
    message?: string;
    severity?: ArchiveRuntimeDecisionSeverity;
    skippedReason?: string;
    createdBy?: number;
    createdAt: string;
}

export interface ArchiveRuntimeSnapshot {
    schemaVersion: string;
    sourceApplicationVersion: string;
    exportedAt: string;
    fileName: string;
    scheme: {
        schemeCode: string;
        schemeName: string;
        versionCode: string;
        versionDescription?: string;
        scopes: Array<{
            scopeType: "GLOBAL" | "FONDS" | "CATEGORY";
            fondsCode?: string;
            categoryCode?: string;
            defaultFlag: boolean;
        }>;
    };
    definitions: unknown[];
    sha256: string;
}

export interface ArchiveRuntimeSnapshotPreflightRequest {
    snapshot: ArchiveRuntimeSnapshot;
    targetSchemeCode?: string;
    categoryMappings?: Record<string, string>;
    fieldMappings?: Record<string, string>;
}

export interface ArchiveRuntimeSnapshotFieldMapping {
    definitionCode: string;
    sourceCategoryCode?: string;
    targetCategoryCode?: string;
    sourceFieldCode: string;
    targetFieldCode: string;
    dataType: ArchiveRuntimeFieldDataType;
}

export interface ArchiveRuntimeSnapshotPreflightResult {
    compatible: boolean;
    targetSchemeCode: string;
    definitionCount: number;
    scopeCount: number;
    fieldMappings: ArchiveRuntimeSnapshotFieldMapping[];
    sha256: string;
}

export interface ArchiveRuntimeSnapshotImportResult {
    schemeVersionId: number;
    schemeCode: string;
    versionCode: string;
    definitionCount: number;
    fieldMappings: ArchiveRuntimeSnapshotFieldMapping[];
    sha256: string;
}

export interface ArchiveRuntimeSnapshotRestoreResult {
    schemeVersionId: number;
    beforeDefinitionCount: number;
    afterDefinitionCount: number;
    fieldMappings: ArchiveRuntimeSnapshotFieldMapping[];
    sha256: string;
}
