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

export interface SearchArchiveRecordsRequest {
    categoryId?: number;
    fondsCode?: string;
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
    tableBuilt: boolean;
    self?: string;
    prev?: string;
    next?: string;
    first?: string;
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
    superAdmin: boolean;
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

export interface CursorPageResponse<T> {
    items: T[];
    self?: string;
    prev?: string;
    next?: string;
    first?: string;
    total?: number;
}

export type ArchiveGovernanceSchemeVersionStatus = "DRAFT" | "PUBLISHED" | "FROZEN" | "RETIRED";
export type ArchiveGovernanceScopeType = "GLOBAL" | "FONDS" | "CATEGORY";
export type ArchiveGovernanceBindingType =
    | "ONTOLOGY"
    | "RULE_SET"
    | "CLASSIFICATION_SCHEME"
    | "DESCRIPTION_PROFILE"
    | "REFERENCE_CODE_RULE";

export interface ArchiveGovernanceSchemeDto {
    id: number;
    schemeCode: string;
    schemeName: string;
    description?: string;
    enabled: boolean;
    sortOrder: number;
}

export interface ArchiveGovernanceSchemeRequest {
    schemeCode: string;
    schemeName: string;
    description?: string;
    enabled?: boolean;
    sortOrder?: number;
}

export interface ArchiveGovernanceSchemeVersionDto {
    id: number;
    schemeId: number;
    versionCode: string;
    versionDescription?: string;
    status: ArchiveGovernanceSchemeVersionStatus;
    publishedBy?: number;
    publishedAt?: string;
    frozenBy?: number;
    frozenAt?: string;
    retiredBy?: number;
    retiredAt?: string;
}

export interface ArchiveGovernanceSchemeVersionRequest {
    versionCode: string;
    versionDescription?: string;
}

export interface ArchiveGovernanceScopeRequest {
    scopeType?: ArchiveGovernanceScopeType;
    fondsCode?: string;
    categoryCode?: string;
    defaultFlag?: boolean;
}

export interface ArchiveGovernanceScopeDto {
    id: number;
    schemeVersionId: number;
    scopeType: ArchiveGovernanceScopeType;
    fondsCode?: string;
    categoryCode?: string;
    defaultFlag: boolean;
}

export interface ArchiveGovernanceBindingRequest {
    bindingType: ArchiveGovernanceBindingType;
    targetType?: string;
    targetId?: number;
    targetCode?: string;
    bindingOrder?: number;
}

export interface ArchiveGovernanceBindingDto {
    id: number;
    schemeVersionId: number;
    bindingType: ArchiveGovernanceBindingType;
    targetType?: string;
    targetId?: number;
    targetCode?: string;
    bindingOrder: number;
}

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

export type ArchiveFieldScope = "METADATA" | "PHYSICAL";

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

export type ArchiveRuleType =
    | "VALIDATION"
    | "DERIVATION"
    | "REFERENCE_CODE"
    | "RETENTION"
    | "ACCESS"
    | "QUALITY"
    | "TRANSFER"
    | "FILING"
    | "EXPORT";
export type ArchiveRuleStatus = "DRAFT" | "PUBLISHED";
export type ArchiveRuleEffectType =
    | "VALIDATION_ERROR"
    | "WARNING"
    | "SUGGEST_VALUE"
    | "DERIVED_VALUE"
    | "REQUIRE_REVIEW"
    | "REQUIRE_QUALITY_CHECK"
    | "DENY_ACCESS"
    | "MASK_FIELD"
    | "INCLUDE_IN_PACKAGE";
export type ArchiveRuleDecisionSeverity = "INFO" | "WARNING" | "ERROR";

export interface ArchiveRuleEffectRequest {
    effectType: ArchiveRuleEffectType;
    effectOrder?: number;
    effectParams?: Record<string, unknown>;
}

export interface ArchiveRuleRequest {
    schemeVersionId: number;
    ruleCode: string;
    ruleName: string;
    ruleType: ArchiveRuleType;
    triggerCode: string;
    scopeFondsCode?: string;
    scopeCategoryCode?: string;
    scopeObjectTypeId?: number;
    scopeArchiveLevel?: ArchiveLevel;
    scopeEventTypeId?: number;
    priority?: number;
    conditionJson: Record<string, unknown>;
    enabled?: boolean;
    effects: ArchiveRuleEffectRequest[];
}

export interface ArchiveRuleEffectDto {
    id: number;
    effectType: ArchiveRuleEffectType;
    effectOrder: number;
    effectParams: Record<string, unknown>;
}

export interface ArchiveRuleDto {
    id: number;
    schemeVersionId: number;
    ruleCode: string;
    ruleName: string;
    ruleType: ArchiveRuleType;
    triggerCode: string;
    status: ArchiveRuleStatus;
    enabled: boolean;
    priority: number;
    effects: ArchiveRuleEffectDto[];
}

export interface ExecuteArchiveRulesRequest {
    schemeVersionId: number;
    triggerCode: string;
    fondsCode?: string;
    categoryCode?: string;
    objectTypeCode?: string;
    archiveLevel?: ArchiveLevel;
    eventCode?: string;
    facts: Record<string, unknown>;
    includeSkipped: boolean;
    recordTrace: boolean;
    userId?: number;
}

export interface ArchiveRuleEffectDecision {
    effectType: ArchiveRuleEffectType;
    params: Record<string, unknown>;
}

export interface ArchiveRuleDecisionDto {
    ruleId?: number;
    ruleCode: string;
    ruleType: ArchiveRuleType;
    matched: boolean;
    effects: ArchiveRuleEffectDecision[];
    message?: string;
    severity: ArchiveRuleDecisionSeverity;
    blocking: boolean;
    skippedReason?: string;
}

export interface SearchArchiveRuleTracesRequest {
    schemeVersionId?: number;
    triggerCode?: string;
    objectTypeCode?: string;
    objectId?: number;
    ruleType?: ArchiveRuleType;
    limit?: number;
    userId?: number;
}

export interface ArchiveRuleTraceDto {
    id: number;
    schemeVersionId: number;
    triggerCode: string;
    objectTypeCode: string;
    objectId?: number;
    ruleId?: number;
    ruleCode?: string;
    ruleType?: ArchiveRuleType;
    matchedFlag: boolean;
    blockingFlag: boolean;
    effectJson: unknown;
    message?: string;
    severity?: ArchiveRuleDecisionSeverity;
    skippedReason?: string;
    createdBy?: number;
    createdAt: string;
}
