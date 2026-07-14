import type { ArchiveFieldType } from "./archive-metadata";

export interface ArchiveLineTableResponse {
    id: number;
    categoryId: number;
    tableCode: string;
    tableName: string;
    physicalTableName: string;
    sortOrder: number;
    enabled: boolean;
    fields: ArchiveLineFieldResponse[];
}

export interface CreateArchiveLineTableRequest {
    tableCode: string;
    tableName: string;
    sortOrder: number;
}

export interface ArchiveLineFieldResponse {
    id: number;
    lineTableId: number;
    fieldCode: string;
    fieldName: string;
    fieldType: ArchiveFieldType;
    columnName: string;
    exactSearchable: boolean;
    sortOrder: number;
    enabled: boolean;
}

export interface CreateArchiveLineFieldRequest {
    fieldCode: string;
    fieldName: string;
    fieldType: ArchiveFieldType;
    columnName: string;
    exactSearchable: boolean;
    sortOrder: number;
}

export interface ArchiveItemLineRowResponse {
    id: number;
    archiveItemId: number;
    lineTableId: number;
    lineOrder: number;
    values: Record<string, unknown | null>;
}

export interface ArchiveItemLineTableDefinitionResponse {
    id: number;
    tableCode: string;
    tableName: string;
    sortOrder: number;
    fields: ArchiveItemLineFieldDefinitionResponse[];
}

export interface ArchiveItemLineFieldDefinitionResponse {
    id: number;
    fieldCode: string;
    fieldName: string;
    fieldType: ArchiveFieldType;
    sortOrder: number;
}

export interface ListArchiveItemLineRowsQuery {
    limit?: number;
    cursor?: string;
}

export interface CreateArchiveItemLineRowRequest {
    lineOrder: number;
    values: Record<string, unknown | null>;
}

export interface PatchArchiveItemLineRowRequest {
    lineOrder?: number;
    values?: Record<string, unknown | null>;
}
