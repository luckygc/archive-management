import { httpClient } from "@archive-management/frontend-core/api";
import type { DownloadLink } from "@archive-management/frontend-core/api";
import type {
    ArchiveCategoryRequest,
    ArchiveCategoryDto,
    CollectionResponse,
    ArchiveFieldRequest,
    ArchiveFieldDto,
    ArchiveFieldLayoutRequest,
    ArchiveFieldLayoutDto,
    ArchiveLevel,
    ArchiveLayoutSurface,
    ArchiveClassificationSchemeDto,
    ArchiveClassificationSchemeRequest,
    ArchiveFondsCategoryScopeDto,
    ArchiveFondsCategoryScopeRequest,
    ArchiveFondsRequest,
    ArchiveFondsDto,
    ArchiveRetentionPeriodRequest,
    ArchiveRetentionPeriodDto,
    CreateArchiveRecordRequest,
    ArchiveRecordDetailDto,
    ArchiveRecordDto,
    ArchiveRecordListDto,
    SearchArchiveRecordsQuery,
    SearchArchiveRecordsRequest,
    UpdateArchiveRecordRequest,
    ArchiveRelatedFilterCategoryDto,
    ArchiveSecurityLevelRequest,
    ArchiveSecurityLevelDto,
    ArchiveUniqueConstraintRequest,
    ArchiveUniqueConstraintDto,
    ArchiveImportResult,
    ArchiveItemAuditDto,
    ListArchiveItemAuditsRequest,
    ArchiveItemElectronicFileUploadOptions,
    ArchiveItemElectronicFileDto,
    ArchiveDataScopeRequest,
    ArchiveDataScopeDto,
    AuthorizationPermissionDto,
    AuthenticationUserDto,
    AuthenticationUserDetailDto,
    CreateAuthenticationUserRequest,
    UpdateAuthenticationUserRequest,
    ResetPasswordRequest,
    SaveUserRolesRequest,
    RoleSummaryDto,
    AuthorizationRoleDto,
    CreateAuthorizationRoleRequest,
    UpdateAuthorizationRoleRequest,
    CursorPageResponse,
    CurrentUserPermissionsDto,
    CreateOrganizationDepartmentRequest,
    DepartmentArchiveDataScopesDto,
    OrganizationDepartmentDto,
    RoleArchiveDataScopesDto,
    RolePermissionsDto,
    UpdateOrganizationDepartmentRequest,
    UserArchiveDataScopesDto,
    ArchiveGovernanceBindingDto,
    ArchiveGovernanceBindingRequest,
    ArchiveGovernanceSchemeDto,
    ArchiveGovernanceSchemeRequest,
    ArchiveGovernanceSchemeVersionDto,
    ArchiveGovernanceSchemeVersionRequest,
    ArchiveGovernanceScopeDto,
    ArchiveGovernanceScopeRequest,
    ArchiveOntologyAttributeMappingDto,
    ArchiveOntologyAttributeMappingRequest,
    ArchiveOntologyAttributeTypeDto,
    ArchiveOntologyAttributeTypeRequest,
    ArchiveOntologyEventTypeDto,
    ArchiveOntologyEventTypeRequest,
    ArchiveOntologyObjectTypeDto,
    ArchiveOntologyObjectTypeRequest,
    ArchiveOntologyRelationTypeDto,
    ArchiveOntologyRelationTypeRequest,
    ArchiveRuleDecisionDto,
    ArchiveRuleDto,
    ArchiveRuleRequest,
    ArchiveRuleStatus,
    ArchiveRuleTraceDto,
    ExecuteArchiveRulesRequest,
    SearchArchiveRuleTracesRequest,
} from "../types/archive";

function queryString(params: Record<string, string | number | boolean | undefined>) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== "") {
            search.set(key, String(value));
        }
    }
    const text = search.toString();
    return text ? `?${text}` : "";
}

export function listArchiveFonds(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveFondsDto>>(
        `/api/v1/archive-fonds${queryString({ enabled })}`,
    );
}

export function createArchiveFonds(payload: ArchiveFondsRequest) {
    return httpClient.post<ArchiveFondsDto>("/api/v1/archive-fonds", payload);
}

export function updateArchiveFonds(id: number, payload: ArchiveFondsRequest) {
    return httpClient.patch<ArchiveFondsDto>(`/api/v1/archive-fonds/${id}`, payload);
}

export function deleteArchiveFonds(id: number) {
    return httpClient.delete<void>(`/api/v1/archive-fonds/${id}`);
}

export function listArchiveFondsCategoryScopes(fondsCode: string) {
    return httpClient.get<CollectionResponse<ArchiveFondsCategoryScopeDto>>(
        `/api/v1/archive-fonds/${fondsCode}/category-scopes`,
    );
}

export function saveArchiveFondsCategoryScopes(
    fondsCode: string,
    payload: ArchiveFondsCategoryScopeRequest[],
) {
    return httpClient.put<CollectionResponse<ArchiveFondsCategoryScopeDto>>(
        `/api/v1/archive-fonds/${fondsCode}/category-scopes`,
        payload,
    );
}

export function listArchiveCategoriesForFonds(fondsCode: string, enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveCategoryDto>>(
        `/api/v1/archive-fonds/${fondsCode}/categories${queryString({ enabled })}`,
    );
}

export function listArchiveClassificationSchemes(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveClassificationSchemeDto>>(
        `/api/v1/archive-classification-schemes${queryString({ enabled })}`,
    );
}

export function createArchiveClassificationScheme(payload: ArchiveClassificationSchemeRequest) {
    return httpClient.post<ArchiveClassificationSchemeDto>(
        "/api/v1/archive-classification-schemes",
        payload,
    );
}

export function updateArchiveClassificationScheme(
    id: number,
    payload: ArchiveClassificationSchemeRequest,
) {
    return httpClient.patch<ArchiveClassificationSchemeDto>(
        `/api/v1/archive-classification-schemes/${id}`,
        payload,
    );
}

export function listArchiveSecurityLevels(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveSecurityLevelDto>>(
        `/api/v1/archive-security-levels${queryString({ enabled })}`,
    );
}

export function updateArchiveSecurityLevel(id: number, payload: ArchiveSecurityLevelRequest) {
    return httpClient.patch<ArchiveSecurityLevelDto>(
        `/api/v1/archive-security-levels/${id}`,
        payload,
    );
}

export function listArchiveRetentionPeriods(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveRetentionPeriodDto>>(
        `/api/v1/archive-retention-periods${queryString({ enabled })}`,
    );
}

export function updateArchiveRetentionPeriod(id: number, payload: ArchiveRetentionPeriodRequest) {
    return httpClient.patch<ArchiveRetentionPeriodDto>(
        `/api/v1/archive-retention-periods/${id}`,
        payload,
    );
}

export function listArchiveCategories(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveCategoryDto>>(
        `/api/v1/archive-categories${queryString({ enabled })}`,
    );
}

export function listArchiveRelatedFilterCategories(categoryId: number) {
    return httpClient.get<CollectionResponse<ArchiveRelatedFilterCategoryDto>>(
        `/api/v1/archive-categories/${categoryId}/related-filter-categories`,
    );
}

export function createArchiveCategory(payload: ArchiveCategoryRequest) {
    return httpClient.post<ArchiveCategoryDto>("/api/v1/archive-categories", payload);
}

export function updateArchiveCategory(id: number, payload: ArchiveCategoryRequest) {
    return httpClient.patch<ArchiveCategoryDto>(`/api/v1/archive-categories/${id}`, payload);
}

export function deleteArchiveCategory(id: number) {
    return httpClient.delete<void>(`/api/v1/archive-categories/${id}`);
}

export function listArchiveFields(categoryId: number, archiveLevel?: ArchiveLevel) {
    return httpClient.get<CollectionResponse<ArchiveFieldDto>>(
        `/api/v1/archive-categories/${categoryId}/fields${queryString({ archiveLevel })}`,
    );
}

export function createArchiveField(categoryId: number, payload: ArchiveFieldRequest) {
    return httpClient.post<ArchiveFieldDto>(
        `/api/v1/archive-categories/${categoryId}/fields`,
        payload,
    );
}

export function updateArchiveField(
    categoryId: number,
    fieldId: number,
    payload: ArchiveFieldRequest,
) {
    return httpClient.patch<ArchiveFieldDto>(
        `/api/v1/archive-categories/${categoryId}/fields/${fieldId}`,
        payload,
    );
}

export function deleteArchiveField(categoryId: number, fieldId: number) {
    return httpClient.delete<void>(`/api/v1/archive-categories/${categoryId}/fields/${fieldId}`);
}

export function getArchiveCategoryLayout(
    categoryId: number,
    surface: ArchiveLayoutSurface,
    archiveLevel?: ArchiveLevel,
) {
    return httpClient.get<ArchiveFieldLayoutDto>(
        `/api/v1/archive-categories/${categoryId}/layouts/${surface}${queryString({ archiveLevel })}`,
    );
}

export function savePublicArchiveCategoryLayout(
    categoryId: number,
    surface: ArchiveLayoutSurface,
    payload: ArchiveFieldLayoutRequest,
    archiveLevel?: ArchiveLevel,
) {
    return httpClient.patch<ArchiveFieldLayoutDto>(
        `/api/v1/archive-categories/${categoryId}/layouts/${surface}${queryString({ archiveLevel })}`,
        payload,
    );
}

export function buildArchiveCategoryTable(categoryId: number, archiveLevel?: ArchiveLevel) {
    return httpClient.post<ArchiveCategoryDto>(
        `/api/v1/archive-categories/${categoryId}:buildTable${queryString({ archiveLevel })}`,
    );
}

export function listArchiveUniqueConstraints(categoryId: number) {
    return httpClient.get<CollectionResponse<ArchiveUniqueConstraintDto>>(
        `/api/v1/archive-categories/${categoryId}/unique-constraints`,
    );
}

export function createArchiveUniqueConstraint(
    categoryId: number,
    payload: ArchiveUniqueConstraintRequest,
) {
    return httpClient.post<ArchiveUniqueConstraintDto>(
        `/api/v1/archive-categories/${categoryId}/unique-constraints`,
        payload,
    );
}

export function updateArchiveUniqueConstraint(
    categoryId: number,
    constraintId: number,
    payload: ArchiveUniqueConstraintRequest,
) {
    return httpClient.patch<ArchiveUniqueConstraintDto>(
        `/api/v1/archive-categories/${categoryId}/unique-constraints/${constraintId}`,
        payload,
    );
}

export function deleteArchiveUniqueConstraint(categoryId: number, constraintId: number) {
    return httpClient.delete<void>(
        `/api/v1/archive-categories/${categoryId}/unique-constraints/${constraintId}`,
    );
}

export function listArchiveRecords(params: { categoryId?: number; fondsCode?: string }) {
    return httpClient.get<ArchiveRecordListDto>(`/api/v1/archive-items${queryString(params)}`);
}

export function searchArchiveRecords(query: SearchArchiveRecordsQuery) {
    const request = archiveRecordSearchRequest(query);
    return httpClient.post<ArchiveRecordListDto>(
        `/api/v1/archive-items:search${request.query}`,
        request.body,
    );
}

export function discoverArchiveRecords(query: SearchArchiveRecordsQuery) {
    const request = archiveRecordSearchRequest(query);
    return httpClient.post<ArchiveRecordListDto>(
        `/api/v1/archive-items:discover${request.query}`,
        request.body,
    );
}

export function createArchiveRecord(payload: CreateArchiveRecordRequest) {
    return httpClient.post<ArchiveRecordDto>("/api/v1/archive-items", payload);
}

export function getArchiveRecord(id: number, surface?: ArchiveLayoutSurface) {
    return httpClient.get<ArchiveRecordDetailDto>(
        `/api/v1/archive-items/${id}${queryString({ surface })}`,
    );
}

export function updateArchiveRecord(id: number, payload: UpdateArchiveRecordRequest) {
    return httpClient.patch<ArchiveRecordDetailDto>(`/api/v1/archive-items/${id}`, payload);
}

export function lockArchiveRecord(id: number, reason?: string) {
    return httpClient.post<ArchiveRecordDto>(`/api/v1/archive-items/${id}:lock`, { reason });
}

export function unlockArchiveRecord(id: number) {
    return httpClient.post<ArchiveRecordDto>(`/api/v1/archive-items/${id}:unlock`);
}

export function downloadArchiveImportTemplate(categoryId: number): DownloadLink {
    return httpClient.download(
        `/api/v1/archive-categories/${categoryId}/archive-items:importTemplate`,
    );
}

export function importArchiveRecords(categoryId: number, file: File) {
    const formData = new FormData();
    formData.set("file", file);
    return httpClient.post<ArchiveImportResult>(
        `/api/v1/archive-categories/${categoryId}/archive-items:import`,
        formData,
    );
}

export function exportArchiveRecords(query: SearchArchiveRecordsQuery): DownloadLink {
    return httpClient.download(
        `/api/v1/archive-items:export${queryString({
            query: encodeDownloadQuery(archiveRecordSearchRequest(query).body),
        })}`,
    );
}

export function listArchiveItemElectronicFiles(archiveItemId: number) {
    return httpClient.get<CollectionResponse<ArchiveItemElectronicFileDto>>(
        `/api/v1/archive-items/${archiveItemId}/electronic-files`,
    );
}

export function uploadArchiveItemElectronicFile(
    archiveItemId: number,
    file: File,
    options: ArchiveItemElectronicFileUploadOptions = {},
) {
    const formData = new FormData();
    formData.set("file", file);
    if (options.usageType) {
        formData.set("usageType", options.usageType);
    }
    if (typeof options.displayOrder === "number") {
        formData.set("displayOrder", String(options.displayOrder));
    }
    return httpClient.post<ArchiveItemElectronicFileDto>(
        `/api/v1/archive-items/${archiveItemId}/electronic-files`,
        formData,
    );
}

export function unbindArchiveItemElectronicFile(archiveItemId: number, electronicFileId: number) {
    return httpClient.delete<void>(
        `/api/v1/archive-items/${archiveItemId}/electronic-files/${electronicFileId}`,
    );
}

interface ArchiveItemElectronicFileDownloadLinkResponse {
    url: string;
    expiresAt: string;
}

export async function downloadArchiveItemElectronicFile(
    archiveItemId: number,
    electronicFileId: number,
): Promise<DownloadLink> {
    const response = await httpClient.post<ArchiveItemElectronicFileDownloadLinkResponse>(
        `/api/v1/archive-items/${archiveItemId}/electronic-files/${electronicFileId}:createDownloadLink`,
    );
    return httpClient.download(response.url);
}

export function listArchiveItemAudits(query: ListArchiveItemAuditsRequest) {
    return httpClient.get<CursorPageResponse<ArchiveItemAuditDto>>(
        `/api/v1/archive-item-audits${queryString({
            archiveItemId: query.archiveItemId,
            fondsCode: query.fondsCode,
            categoryCode: query.categoryCode,
            operationType: query.operationType,
            operatedAfter: query.operatedAfter,
            operatedBefore: query.operatedBefore,
            limit: query.limit,
            cursor: query.cursor,
            requestTotal: query.requestTotal,
        })}`,
    );
}

export function listAuthorizationPermissions() {
    return httpClient.get<CollectionResponse<AuthorizationPermissionDto>>(
        "/api/v1/authorization-permissions",
    );
}

export function getCurrentUserPermissions() {
    return httpClient.get<CurrentUserPermissionsDto>("/api/v1/me/permissions");
}

export function getRolePermissions(roleId: number) {
    return httpClient.get<RolePermissionsDto>(`/api/v1/authorization-roles/${roleId}/permissions`);
}

export function saveRolePermissions(roleId: number, permissionCodes: string[]) {
    return httpClient.put<RolePermissionsDto>(`/api/v1/authorization-roles/${roleId}/permissions`, {
        permissionCodes,
    });
}

export function listArchiveDataScopes(enabled = true) {
    return httpClient.get<CollectionResponse<ArchiveDataScopeDto>>(
        `/api/v1/archive-data-scopes${queryString({ enabled })}`,
    );
}

export function createArchiveDataScope(payload: ArchiveDataScopeRequest) {
    return httpClient.post<ArchiveDataScopeDto>("/api/v1/archive-data-scopes", payload);
}

export function updateArchiveDataScope(id: number, payload: ArchiveDataScopeRequest) {
    return httpClient.put<ArchiveDataScopeDto>(`/api/v1/archive-data-scopes/${id}`, payload);
}

export function listArchiveDataScopeFields(categoryId: number) {
    return httpClient.get<CollectionResponse<ArchiveFieldDto>>(
        `/api/v1/archive-categories/${categoryId}/data-scope-fields`,
    );
}

export function listOrganizationDepartments(enabled?: boolean) {
    return httpClient.get<CollectionResponse<OrganizationDepartmentDto>>(
        `/api/v1/organization-departments${queryString({ enabled })}`,
    );
}

export function getOrganizationDepartment(id: number) {
    return httpClient.get<OrganizationDepartmentDto>(`/api/v1/organization-departments/${id}`);
}

export function createOrganizationDepartment(payload: CreateOrganizationDepartmentRequest) {
    return httpClient.post<OrganizationDepartmentDto>("/api/v1/organization-departments", payload);
}

export function updateOrganizationDepartment(
    id: number,
    payload: UpdateOrganizationDepartmentRequest,
) {
    return httpClient.patch<OrganizationDepartmentDto>(
        `/api/v1/organization-departments/${id}`,
        payload,
    );
}

export function getRoleArchiveDataScopes(roleId: number) {
    return httpClient.get<RoleArchiveDataScopesDto>(
        `/api/v1/authorization-roles/${roleId}/archive-data-scopes`,
    );
}

export function saveRoleArchiveDataScopes(roleId: number, scopeIds: number[]) {
    return httpClient.put<RoleArchiveDataScopesDto>(
        `/api/v1/authorization-roles/${roleId}/archive-data-scopes`,
        { scopeIds },
    );
}

function archiveRecordSearchRequest(query: SearchArchiveRecordsQuery): {
    query: string;
    body: SearchArchiveRecordsRequest;
} {
    const { limit, cursor, requestTotal, ...body } = query;
    return {
        query: queryString({ limit, cursor, requestTotal }),
        body: compactObject(body),
    };
}

function compactObject<T extends object>(value: T): T {
    return Object.fromEntries(
        Object.entries(value).filter(([, entryValue]) => entryValue !== undefined),
    ) as T;
}

function encodeDownloadQuery(query: SearchArchiveRecordsRequest) {
    const bytes = new TextEncoder().encode(JSON.stringify(query));
    let binary = "";
    bytes.forEach((byte) => {
        binary += String.fromCharCode(byte);
    });
    return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

export function getUserArchiveDataScopes(userId: number) {
    return httpClient.get<UserArchiveDataScopesDto>(
        `/api/v1/authorization-users/${userId}/archive-data-scopes`,
    );
}

export function saveUserArchiveDataScopes(userId: number, scopeIds: number[]) {
    return httpClient.put<UserArchiveDataScopesDto>(
        `/api/v1/authorization-users/${userId}/archive-data-scopes`,
        { scopeIds },
    );
}

export function getDepartmentArchiveDataScopes(departmentId: number) {
    return httpClient.get<DepartmentArchiveDataScopesDto>(
        `/api/v1/organization-departments/${departmentId}/archive-data-scopes`,
    );
}

export function saveDepartmentArchiveDataScopes(departmentId: number, scopeIds: number[]) {
    return httpClient.put<DepartmentArchiveDataScopesDto>(
        `/api/v1/organization-departments/${departmentId}/archive-data-scopes`,
        { scopeIds },
    );
}

// ────────────────────────────── 用户管理 ──────────────────────────────

export function listAuthenticationUsers(keyword?: string, limit = 100, cursor?: string) {
    const params = new URLSearchParams();
    if (keyword) {
        params.set("keyword", keyword);
    }
    params.set("limit", String(limit));
    if (cursor) {
        params.set("cursor", cursor);
    }
    return httpClient.get<CursorPageResponse<AuthenticationUserDto>>(
        `/api/v1/authentication-users?${params.toString()}`,
    );
}

export function createAuthenticationUser(payload: CreateAuthenticationUserRequest) {
    return httpClient.post<AuthenticationUserDto>("/api/v1/authentication-users", payload);
}

export function getAuthenticationUser(id: number) {
    return httpClient.get<AuthenticationUserDetailDto>(`/api/v1/authentication-users/${id}`);
}

export function updateAuthenticationUser(id: number, payload: UpdateAuthenticationUserRequest) {
    return httpClient.patch<AuthenticationUserDto>(`/api/v1/authentication-users/${id}`, payload);
}

export function resetAuthenticationUserPassword(id: number, payload: ResetPasswordRequest) {
    return httpClient.post<void>(`/api/v1/authentication-users/${id}:resetPassword`, payload);
}

export function listAuthenticationUserRoles(id: number) {
    return httpClient.get<CollectionResponse<RoleSummaryDto>>(
        `/api/v1/authentication-users/${id}/roles`,
    );
}

export function saveAuthenticationUserRoles(id: number, payload: SaveUserRolesRequest) {
    return httpClient.put<CollectionResponse<RoleSummaryDto>>(
        `/api/v1/authentication-users/${id}/roles`,
        payload,
    );
}

// ────────────────────────────── 角色管理 ──────────────────────────────

export function listAuthorizationRoles(enabled?: boolean, limit = 100, cursor?: string) {
    const params = new URLSearchParams();
    if (enabled !== undefined) {
        params.set("enabled", String(enabled));
    }
    params.set("limit", String(limit));
    if (cursor) {
        params.set("cursor", cursor);
    }
    return httpClient.get<CursorPageResponse<AuthorizationRoleDto>>(
        `/api/v1/authorization-roles?${params.toString()}`,
    );
}

export function createAuthorizationRole(payload: CreateAuthorizationRoleRequest) {
    return httpClient.post<AuthorizationRoleDto>("/api/v1/authorization-roles", payload);
}

export function updateAuthorizationRole(id: number, payload: UpdateAuthorizationRoleRequest) {
    return httpClient.patch<AuthorizationRoleDto>(`/api/v1/authorization-roles/${id}`, payload);
}

export function deleteAuthorizationRole(id: number) {
    return httpClient.delete<void>(`/api/v1/authorization-roles/${id}`);
}

export function listArchiveGovernanceSchemes(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveGovernanceSchemeDto>>(
        `/api/v1/archive-governance-schemes${queryString({ enabled })}`,
    );
}

export function createArchiveGovernanceScheme(payload: ArchiveGovernanceSchemeRequest) {
    return httpClient.post<ArchiveGovernanceSchemeDto>(
        "/api/v1/archive-governance-schemes",
        payload,
    );
}

export function updateArchiveGovernanceScheme(id: number, payload: ArchiveGovernanceSchemeRequest) {
    return httpClient.patch<ArchiveGovernanceSchemeDto>(
        `/api/v1/archive-governance-schemes/${id}`,
        payload,
    );
}

export function deleteArchiveGovernanceScheme(id: number) {
    return httpClient.delete<void>(`/api/v1/archive-governance-schemes/${id}`);
}

export function listArchiveGovernanceSchemeVersions(schemeId: number) {
    return httpClient.get<CollectionResponse<ArchiveGovernanceSchemeVersionDto>>(
        `/api/v1/archive-governance-schemes/${schemeId}/versions`,
    );
}

export function createArchiveGovernanceSchemeVersion(
    schemeId: number,
    payload: ArchiveGovernanceSchemeVersionRequest,
) {
    return httpClient.post<ArchiveGovernanceSchemeVersionDto>(
        `/api/v1/archive-governance-schemes/${schemeId}/versions`,
        payload,
    );
}

export function publishArchiveGovernanceSchemeVersion(id: number) {
    return httpClient.post<ArchiveGovernanceSchemeVersionDto>(
        `/api/v1/archive-governance-scheme-versions/${id}:publish`,
    );
}

export function freezeArchiveGovernanceSchemeVersion(id: number) {
    return httpClient.post<ArchiveGovernanceSchemeVersionDto>(
        `/api/v1/archive-governance-scheme-versions/${id}:freeze`,
    );
}

export function retireArchiveGovernanceSchemeVersion(id: number) {
    return httpClient.post<ArchiveGovernanceSchemeVersionDto>(
        `/api/v1/archive-governance-scheme-versions/${id}:retire`,
    );
}

export function resolveDefaultArchiveGovernanceVersion(params: {
    fondsCode?: string;
    categoryCode?: string;
}) {
    return httpClient.get<ArchiveGovernanceSchemeVersionDto>(
        `/api/v1/archive-governance-scheme-versions:resolveDefault${queryString(params)}`,
    );
}

export function listArchiveGovernanceScopes(versionId: number) {
    return httpClient.get<CollectionResponse<ArchiveGovernanceScopeDto>>(
        `/api/v1/archive-governance-scheme-versions/${versionId}/scopes`,
    );
}

export function replaceArchiveGovernanceScopes(
    versionId: number,
    payload: ArchiveGovernanceScopeRequest[],
) {
    return httpClient.put<CollectionResponse<ArchiveGovernanceScopeDto>>(
        `/api/v1/archive-governance-scheme-versions/${versionId}/scopes`,
        payload,
    );
}

export function listArchiveGovernanceBindings(versionId: number) {
    return httpClient.get<CollectionResponse<ArchiveGovernanceBindingDto>>(
        `/api/v1/archive-governance-scheme-versions/${versionId}/bindings`,
    );
}

export function replaceArchiveGovernanceBindings(
    versionId: number,
    payload: ArchiveGovernanceBindingRequest[],
) {
    return httpClient.put<CollectionResponse<ArchiveGovernanceBindingDto>>(
        `/api/v1/archive-governance-scheme-versions/${versionId}/bindings`,
        payload,
    );
}

export function listArchiveOntologyObjectTypes(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveOntologyObjectTypeDto>>(
        `/api/v1/archive-ontology-object-types${queryString({ enabled })}`,
    );
}

export function createArchiveOntologyObjectType(payload: ArchiveOntologyObjectTypeRequest) {
    return httpClient.post<ArchiveOntologyObjectTypeDto>(
        "/api/v1/archive-ontology-object-types",
        payload,
    );
}

export function initializeArchiveOntologyObjectTypes() {
    return httpClient.post<CollectionResponse<ArchiveOntologyObjectTypeDto>>(
        "/api/v1/archive-ontology-object-types:initializeBuiltins",
    );
}

export function listArchiveOntologyAttributeTypes(objectTypeId?: number) {
    return httpClient.get<CollectionResponse<ArchiveOntologyAttributeTypeDto>>(
        `/api/v1/archive-ontology-attribute-types${queryString({ objectTypeId })}`,
    );
}

export function createArchiveOntologyAttributeType(payload: ArchiveOntologyAttributeTypeRequest) {
    return httpClient.post<ArchiveOntologyAttributeTypeDto>(
        "/api/v1/archive-ontology-attribute-types",
        payload,
    );
}

export function listArchiveOntologyAttributeMappings(attributeTypeId?: number) {
    return httpClient.get<CollectionResponse<ArchiveOntologyAttributeMappingDto>>(
        `/api/v1/archive-ontology-attribute-mappings${queryString({ attributeTypeId })}`,
    );
}

export function createArchiveOntologyAttributeMapping(
    payload: ArchiveOntologyAttributeMappingRequest,
) {
    return httpClient.post<ArchiveOntologyAttributeMappingDto>(
        "/api/v1/archive-ontology-attribute-mappings",
        payload,
    );
}

export function deleteArchiveOntologyAttributeMapping(id: number) {
    return httpClient.delete<void>(`/api/v1/archive-ontology-attribute-mappings/${id}`);
}

export function listArchiveOntologyRelationTypes() {
    return httpClient.get<CollectionResponse<ArchiveOntologyRelationTypeDto>>(
        "/api/v1/archive-ontology-relation-types",
    );
}

export function createArchiveOntologyRelationType(payload: ArchiveOntologyRelationTypeRequest) {
    return httpClient.post<ArchiveOntologyRelationTypeDto>(
        "/api/v1/archive-ontology-relation-types",
        payload,
    );
}

export function listArchiveOntologyEventTypes() {
    return httpClient.get<CollectionResponse<ArchiveOntologyEventTypeDto>>(
        "/api/v1/archive-ontology-event-types",
    );
}

export function createArchiveOntologyEventType(payload: ArchiveOntologyEventTypeRequest) {
    return httpClient.post<ArchiveOntologyEventTypeDto>(
        "/api/v1/archive-ontology-event-types",
        payload,
    );
}

export function initializeArchiveOntologyEventTypes() {
    return httpClient.post<CollectionResponse<ArchiveOntologyEventTypeDto>>(
        "/api/v1/archive-ontology-event-types:initializeBuiltins",
    );
}

export function listArchiveRules(schemeVersionId: number, status?: ArchiveRuleStatus) {
    return httpClient.get<CollectionResponse<ArchiveRuleDto>>(
        `/api/v1/archive-rules${queryString({ schemeVersionId, status })}`,
    );
}

export function createArchiveRule(payload: ArchiveRuleRequest) {
    return httpClient.post<ArchiveRuleDto>("/api/v1/archive-rules", payload);
}

export function publishArchiveRule(id: number) {
    return httpClient.post<ArchiveRuleDto>(`/api/v1/archive-rules/${id}:publish`);
}

export function enableArchiveRule(id: number) {
    return httpClient.post<ArchiveRuleDto>(`/api/v1/archive-rules/${id}:enable`);
}

export function disableArchiveRule(id: number) {
    return httpClient.post<ArchiveRuleDto>(`/api/v1/archive-rules/${id}:disable`);
}

export function executeArchiveRules(payload: ExecuteArchiveRulesRequest) {
    return httpClient.post<CollectionResponse<ArchiveRuleDecisionDto>>(
        "/api/v1/archive-rules:execute",
        payload,
    );
}

export function searchArchiveRuleTraces(payload: SearchArchiveRuleTracesRequest) {
    return httpClient.post<CollectionResponse<ArchiveRuleTraceDto>>(
        "/api/v1/archive-rule-traces:search",
        payload,
    );
}
