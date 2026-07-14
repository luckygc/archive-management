export type ArchiveFieldType = "TEXT" | "INTEGER" | "DECIMAL" | "DATE" | "DATETIME";
export type ArchiveFieldControl = "INPUT" | "TEXTAREA" | "NUMBER" | "DATE" | "DATETIME";
export type ArchiveTableStatus = "NOT_BUILT" | "BUILT";
export type ArchiveLayoutSurface = "TABLE" | "DETAIL" | "EDIT";
export type ArchiveLayoutScope = "public" | "effective";
export type ArchiveLevel = "VOLUME" | "ITEM";
export type ArchiveManagementMode = "ITEM_ONLY" | "VOLUME_ITEM";

export interface ArchiveFondsDto {
    id: number;
    fondsCode: string;
    fondsName: string;
    enabled: boolean;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface ArchiveFondsRequest {
    fondsCode: string;
    fondsName: string;
    enabled: boolean;
    sortOrder: number;
}

export interface ArchiveClassificationSchemeDto {
    id: number;
    schemeCode: string;
    schemeName: string;
    description?: string;
    defaultFlag: boolean;
    enabled: boolean;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface ArchiveClassificationSchemeRequest {
    schemeCode: string;
    schemeName: string;
    description?: string;
    enabled: boolean;
    sortOrder: number;
}

export interface ArchiveFondsCategoryScopeDto {
    id?: number;
    fondsCode: string;
    categoryId: number;
    defaultFlag: boolean;
    sortOrder: number;
    createdAt?: string;
    updatedAt?: string;
}

export interface ArchiveFondsCategoryScopeRequest {
    categoryId: number;
    defaultFlag: boolean;
    sortOrder: number;
}

export interface ArchiveSecurityLevelDto {
    id: number;
    levelName: string;
    enabled: boolean;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface ArchiveSecurityLevelRequest {
    levelName: string;
}

export interface ArchiveRetentionPeriodDto {
    id: number;
    periodName: string;
    enabled: boolean;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface ArchiveRetentionPeriodRequest {
    periodName: string;
}

export interface ArchiveCategoryDto {
    id: number;
    schemeId: number;
    parentId?: number;
    categoryCode: string;
    categoryName: string;
    managementMode: ArchiveManagementMode;
    volumeTableName?: string;
    itemTableName?: string;
    tableStatus: ArchiveTableStatus;
    builtAt?: string;
    enabled: boolean;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface ArchiveCategoryRequest {
    schemeId: number;
    categoryCode: string;
    categoryName: string;
    parentId?: number;
    managementMode: ArchiveManagementMode;
    enabled: boolean;
    sortOrder: number;
}

export interface ArchiveFieldDto {
    id: number;
    categoryId: number;
    archiveLevel: ArchiveLevel;
    fieldCode: string;
    fieldName: string;
    fieldType: ArchiveFieldType;
    columnName: string;
    textLength?: number;
    decimalPrecision?: number;
    decimalScale?: number;
    editControl: ArchiveFieldControl;
    listVisible: boolean;
    listWidth?: number;
    listSortOrder: number;
    detailVisible: boolean;
    detailColSpan: number;
    detailSortOrder: number;
    editVisible: boolean;
    editColSpan: number;
    editSortOrder: number;
    exactSearchable: boolean;
    dataScopeFilterable: boolean;
    enabled: boolean;
    sortOrder: number;
    fieldSource?: "BUILTIN" | "METADATA";
    createdAt: string;
    updatedAt: string;
}

export interface ArchiveFieldRequest {
    archiveLevel: ArchiveLevel;
    fieldCode: string;
    fieldName: string;
    fieldType: ArchiveFieldType;
    textLength?: number;
    decimalPrecision?: number;
    decimalScale?: number;
    editControl?: ArchiveFieldControl;
    listVisible: boolean;
    listWidth?: number;
    listSortOrder: number;
    detailVisible: boolean;
    detailColSpan: number;
    detailSortOrder: number;
    editVisible: boolean;
    editColSpan: number;
    editSortOrder: number;
    exactSearchable: boolean;
    dataScopeFilterable: boolean;
    enabled: boolean;
    sortOrder: number;
}

export interface ArchiveFieldLayoutDto {
    surface: ArchiveLayoutSurface;
    scope: ArchiveLayoutScope;
    items: ArchiveFieldLayoutItemDto[];
}

export interface ArchiveFieldLayoutItemDto {
    fieldId: number;
    fieldCode: string;
    fieldName: string;
    fieldType: ArchiveFieldType;
    editControl: ArchiveFieldControl;
    visible: boolean;
    listWidth?: number;
    colSpan: number;
    rowOrder: number;
    colOrder: number;
}

export interface ArchiveFieldLayoutRequest {
    items: ArchiveFieldLayoutItemRequest[];
}

export interface ArchiveFieldLayoutItemRequest {
    fieldId: number;
    visible: boolean;
    listWidth?: number;
    colSpan: number;
    rowOrder: number;
    colOrder: number;
}

export interface ArchiveUniqueConstraintFieldDto {
    fieldId: number;
    fieldOrder: number;
    archiveLevel: ArchiveLevel;
    fieldCode: string;
    fieldName: string;
    columnName: string;
}

export interface ArchiveUniqueConstraintDto {
    id: number;
    categoryId: number;
    archiveLevel: ArchiveLevel;
    constraintCode: string;
    constraintName: string;
    indexName: string;
    enabled: boolean;
    fields: ArchiveUniqueConstraintFieldDto[];
    createdAt: string;
    updatedAt: string;
}

export interface ArchiveUniqueConstraintRequest {
    archiveLevel: ArchiveLevel;
    constraintCode: string;
    constraintName: string;
    enabled: boolean;
    fieldIds: number[];
}

export type ArchiveFieldScope = "METADATA" | "PHYSICAL";
