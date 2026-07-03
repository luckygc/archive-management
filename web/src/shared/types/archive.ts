export type ArchiveFieldType = "TEXT" | "INTEGER" | "DECIMAL" | "DATE" | "DATETIME";
export type ArchiveFieldControl = "INPUT" | "TEXTAREA" | "NUMBER" | "DATE" | "DATETIME";
export type ArchiveTableStatus = "NOT_BUILT" | "BUILT";
export type ArchiveLayoutSurface = "TABLE" | "DETAIL" | "EDIT";
export type ArchiveLayoutScope = "public" | "effective";
export type ArchiveLevel = "VOLUME" | "ITEM";
export type ArchiveManagementMode = "ITEM_ONLY" | "VOLUME_ITEM";
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

export interface CollectionResponse<T> {
    items: T[];
}

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

export interface SearchArchiveRecordsRequest {
    categoryId?: number;
    fondsCode?: string;
    keyword?: string;
    where?: ArchiveItemWhere;
    relatedGroups?: ArchiveItemRelatedGroup[];
    limit?: number;
    cursor?: string;
    orderBy?: ArchiveRecordOrderBy[];
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
    logic?: "AND";
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

export interface CreateArchiveRecordRequest {
    categoryId: number;
    volumeId?: number;
    fondsCode: string;
    archiveNo?: string;
    archiveYear?: number;
    electronicStatus?: ArchiveElectronicStatus;
    securityLevelId?: number;
    retentionPeriodId?: number;
    physicalObject: ArchivePhysicalObjectRequest;
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
    physicalObject: ArchivePhysicalObjectRequest;
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
    tableBuilt: boolean;
    self?: string;
    prev?: string;
    next?: string;
    first?: string;
    items: Record<string, unknown>[];
}

export interface ArchiveRecordDetailDto {
    record: ArchiveRecordDto;
    category: ArchiveCategoryDto;
    fields: ArchiveFieldDto[];
    dynamicFields: Record<string, unknown>;
    physicalObject?: ArchivePhysicalObjectDto;
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

export type ArchiveDataScopeType = "ALL" | "CONDITIONAL";
export type ArchiveDataScopeDimensionType =
    | "FONDS"
    | "CATEGORY"
    | "SECURITY_LEVEL"
    | "RETENTION_PERIOD";

export interface ArchiveDataScopeDto {
    id: number;
    scopeCode: string;
    scopeName: string;
    scopeType: ArchiveDataScopeType;
    dimensions: ArchiveDataScopeDimensionDto[];
    dynamicCondition?: ArchiveDataScopeDynamicCondition;
    enabled: boolean;
    description?: string;
}

export interface ArchiveDataScopeDimensionDto {
    dimensionType: ArchiveDataScopeDimensionType;
    targetId?: number;
    targetCode?: string;
    includeDescendants: boolean;
    sortOrder: number;
}

export interface ArchiveDataScopeDynamicCondition {
    dynamicFields: ArchiveDataScopeDynamicFieldCondition[];
}

export interface ArchiveDataScopeDynamicFieldCondition {
    categoryId: number;
    fieldCode: string;
    operator: "EQ" | "IN" | "IS_NULL" | "IS_NOT_NULL";
    values: string[];
}

export interface ArchiveDataScopeRequest {
    scopeCode: string;
    scopeName: string;
    scopeType: ArchiveDataScopeType;
    dimensions: ArchiveDataScopeDimensionRequest[];
    dynamicCondition?: ArchiveDataScopeDynamicCondition;
    enabled: boolean;
    description?: string;
}

export interface ArchiveDataScopeDimensionRequest {
    dimensionType: ArchiveDataScopeDimensionType;
    targetId?: number;
    targetCode?: string;
    includeDescendants: boolean;
}

export interface AuthorizationPermissionDto {
    permissionCode: string;
    permissionName: string;
    moduleCode: string;
    description: string;
}

export interface AuthenticationUserDto {
    id: number;
    username: string;
    displayName: string;
    email?: string;
    mobilePhone?: string;
    departmentId?: number;
    departmentCode?: string;
    departmentName?: string;
    enabled: boolean;
    createdAt: string;
}

export interface AuthenticationUserDetailDto extends AuthenticationUserDto {
    roles: RoleSummaryDto[];
}

export interface RoleSummaryDto {
    id: number;
    roleName: string;
}

export interface CreateAuthenticationUserRequest {
    username: string;
    password: string;
    displayName: string;
    email?: string;
    mobilePhone?: string;
    departmentId?: number | null;
}

export interface UpdateAuthenticationUserRequest {
    displayName?: string;
    email?: string | null;
    mobilePhone?: string | null;
    departmentId?: number | null;
    enabled?: boolean;
}

export interface ResetPasswordRequest {
    newPassword: string;
}

export interface SaveUserRolesRequest {
    roleIds: number[];
}

export interface AuthorizationRoleDto {
    id: number;
    roleName: string;
    description?: string;
    enabled: boolean;
    createdAt: string;
}

export interface CreateAuthorizationRoleRequest {
    roleName: string;
    description?: string;
}

export interface UpdateAuthorizationRoleRequest {
    roleName?: string;
    description?: string;
    enabled?: boolean;
}

export interface CurrentUserPermissionsDto {
    permissionCodes: string[];
}

export interface RolePermissionsDto {
    roleId: number;
    permissionCodes: string[];
}

export interface RoleArchiveDataScopesDto {
    roleId: number;
    scopeIds: number[];
}

export interface UserArchiveDataScopesDto {
    userId: number;
    scopeIds: number[];
}

export interface DepartmentArchiveDataScopesDto {
    departmentId: number;
    scopeIds: number[];
}

export interface OrganizationDepartmentDto {
    id: number;
    departmentCode: string;
    departmentName: string;
    parentId?: number;
    enabled: boolean;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface CreateOrganizationDepartmentRequest {
    departmentCode?: string;
    departmentName?: string;
    parentId?: number | null;
    enabled?: boolean;
    sortOrder?: number;
}

export interface UpdateOrganizationDepartmentRequest {
    departmentCode?: string;
    departmentName?: string;
    parentId?: number | null;
    enabled?: boolean;
    sortOrder?: number;
}

export interface ArchiveItemElectronicFileRequest {
    storageObjectId: number;
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

export interface CursorPageResponse<T> {
    items: T[];
    self?: string;
    prev?: string;
    next?: string;
    first?: string;
    total?: number;
}
