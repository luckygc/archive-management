import type { ArchiveCategoryDto, ArchiveFieldDto } from "./archive-metadata";

export type ArchiveElectronicStatus = "DRAFT" | "ARCHIVED" | "BORROWED";
export type ArchivePhysicalStatus =
    | "NONE"
    | "REGISTERED"
    | "TRANSFERRING"
    | "IN_STORAGE"
    | "BORROWED";
export type ArchiveItemQueryOperator =
    | "EQ"
    | "IN"
    | "CONTAINS"
    | "STARTS_WITH"
    | "GTE"
    | "LTE"
    | "BETWEEN"
    | "IS_NULL"
    | "IS_NOT_NULL"
    | "IS_EMPTY"
    | "IS_NOT_EMPTY";
export type ArchiveItemRelationDirection = "OUTGOING" | "INCOMING" | "BOTH";

export interface SearchArchiveRecordsRequest {
    categoryId?: number;
    fondsCode?: string;
    volumeId?: number;
    keyword?: string;
    where?: ArchiveItemWhere;
    relatedGroups?: ArchiveItemRelatedGroup[];
    orderBy?: ArchiveRecordOrderBy[];
}

export interface SearchArchiveRecordsQuery extends SearchArchiveRecordsRequest {
    limit?: number;
    cursor?: string;
    requestTotal?: boolean;
}

export interface ArchiveRecordOrderBy {
    field: ArchiveRecordSortField;
    direction: "ASC" | "DESC";
}

export type ArchiveRecordSortField =
    | "createdAt"
    | "archiveNo"
    | "archiveYear"
    | "fondsCode"
    | "categoryCode"
    | "electronicStatus"
    | "id"
    | (string & {});

export interface ArchiveItemWhere {
    conditions?: ArchiveItemQueryCondition[];
}

export interface ArchiveItemQueryCondition {
    fieldCode: string;
    op?: ArchiveItemQueryOperator;
    value?: unknown;
    startValue?: unknown;
    endValue?: unknown;
}

export interface ArchiveItemRelatedGroup {
    categoryId: number;
    direction?: ArchiveItemRelationDirection;
    where?: ArchiveItemWhere;
}

export interface ArchiveRelatedFilterCategoryDto {
    categoryId: number;
    categoryCode: string;
    categoryName: string;
    direction: ArchiveItemRelationDirection;
}

export interface ListArchiveItemRelationsQuery {
    depth?: number;
    limit?: number;
    cursor?: string;
    requestTotal?: boolean;
}

export interface ArchiveItemRelationResponse {
    id: number;
    sourceItemId: number;
    targetItemId: number;
    relatedItemId: number;
    direction: "OUTGOING" | "INCOMING";
    createdAt?: string;
    relatedItem: ArchiveItemRelationTargetResponse;
}

export interface ArchiveItemRelationTargetResponse {
    itemId: number;
    fondsCode: string;
    fondsName: string;
    categoryCode: string;
    categoryName: string;
    archiveNo?: string;
}

export interface CreateArchiveRecordRequest {
    categoryId: number;
    volumeId?: number;
    fondsCode: string;
    archiveNo?: string;
    archiveYear?: number;
    electronicStatus?: ArchiveElectronicStatus;
    securityLevelId?: number;
    retentionPeriodId?: number;
    physicalFields?: Record<string, unknown>;
    dynamicFields: Record<string, unknown>;
}

export interface UpdateArchiveRecordRequest {
    volumeId?: number;
    fondsCode: string;
    archiveNo?: string;
    archiveYear?: number;
    electronicStatus?: ArchiveElectronicStatus;
    securityLevelId?: number;
    retentionPeriodId?: number;
    physicalFields?: Record<string, unknown>;
    dynamicFields: Record<string, unknown>;
}

export interface ArchivePhysicalObjectRequest {
    physicalStatus: ArchivePhysicalStatus;
    boxNo?: string;
    locationNo?: string;
    barcode?: string;
    remark?: string;
}

export interface ArchiveRecordDto {
    id: number;
    volumeId?: number;
    fondsCode: string;
    fondsName: string;
    categoryCode: string;
    categoryName: string;
    archiveNo?: string;
    electronicStatus: ArchiveElectronicStatus;
    securityLevelId?: number;
    retentionPeriodId?: number;
    archiveYear: number;
    lockedFlag: boolean;
    lockReason?: string;
    lockedBy?: number;
    lockedAt?: string;
}

export interface ArchivePhysicalObjectDto {
    id: number;
    archiveRecordId: number;
    physicalStatus: ArchivePhysicalStatus;
    boxNo?: string;
    locationNo?: string;
    barcode?: string;
    remark?: string;
}

export interface ArchiveRecordListDto {
    category?: ArchiveCategoryDto;
    fields: ArchiveFieldDto[];
    self?: string;
    prev?: string;
    next?: string;
    first?: string;
    total?: number;
    items: Record<string, unknown>[];
}

export interface ArchiveRecordDetailDto {
    item: ArchiveRecordDto;
    category: ArchiveCategoryDto;
    fields: ArchiveFieldDto[];
    dynamicFields: Record<string, unknown>;
    physicalFields: ArchiveFieldDto[];
    physicalFieldValues: Record<string, unknown>;
}

export interface SearchProjectionRebuildResult {
    categoryId: number;
    rebuiltCount: number;
}

export interface ArchiveImportResult {
    importedCount: number;
    errors: ArchiveImportRowError[];
}

export interface ArchiveImportRowError {
    rowNumber: number;
    fieldName: string;
    message: string;
}

export interface ArchiveItemElectronicFileUploadOptions {
    usageType?: string;
    displayOrder?: number;
}

export interface ArchiveItemElectronicFileDto {
    id: number;
    archiveItemId: number;
    storageObjectId: number;
    usageType: string;
    displayOrder: number;
    originalFilename: string;
    fileSize: number;
    contentType?: string;
    checksumSha256?: string;
    createdAt: string;
}

export interface ArchiveItemAuditDto {
    id: number;
    sourceTableName: string;
    sourceItemId: number;
    archiveItemId?: number;
    fondsCode?: string;
    categoryCode?: string;
    operationType: string;
    operationReason?: string;
    operatedBy: number;
    operatedAt: string;
}

export interface ListArchiveItemAuditsRequest {
    archiveItemId?: number;
    fondsCode?: string;
    categoryCode?: string;
    operationType?: string;
    operatedAfter?: string;
    operatedBefore?: string;
    limit?: number;
    cursor?: string;
    requestTotal?: boolean;
}
