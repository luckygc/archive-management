import { httpClient } from "@archive-management/frontend-core/api";
import type { ArchiveFieldDto } from "../types/archive-metadata";
import type {
    ArchiveDataScopeDto,
    ArchiveDataScopeRequest,
    AuthorizationPermissionDto,
    AuthorizationRoleDto,
    CreateAuthorizationRoleRequest,
    CurrentUserPermissionsDto,
    DepartmentArchiveDataScopesDto,
    RoleArchiveDataScopesDto,
    RolePermissionsDto,
    UpdateAuthorizationRoleRequest,
    UserArchiveDataScopesDto,
} from "../types/authorization";
import type { CollectionResponse, CursorPageResponse } from "../types/pagination";
import { pageUrl, queryString } from "./query-string";

export function listAuthorizationPermissions() {
    return httpClient.get<CollectionResponse<AuthorizationPermissionDto>>(
        "/authorization-permissions",
    );
}

export function getCurrentUserPermissions() {
    return httpClient.get<CurrentUserPermissionsDto>("/me/permissions");
}

export function getRolePermissions(roleId: number) {
    return httpClient.get<RolePermissionsDto>(`/authorization-roles/${roleId}/permissions`);
}

export function saveRolePermissions(roleId: number, permissionCodes: string[]) {
    return httpClient.put<RolePermissionsDto>(`/authorization-roles/${roleId}/permissions`, {
        permissionCodes,
    });
}

export function listArchiveDataScopes(enabled = true) {
    return httpClient.get<CollectionResponse<ArchiveDataScopeDto>>(
        `/archive-data-scopes${queryString({ enabled })}`,
    );
}

export function createArchiveDataScope(payload: ArchiveDataScopeRequest) {
    return httpClient.post<ArchiveDataScopeDto>("/archive-data-scopes", payload);
}

export function updateArchiveDataScope(id: number, payload: ArchiveDataScopeRequest) {
    return httpClient.put<ArchiveDataScopeDto>(`/archive-data-scopes/${id}`, payload);
}

export function listArchiveDataScopeFields(categoryId: number) {
    return httpClient.get<CollectionResponse<ArchiveFieldDto>>(
        `/archive-categories/${categoryId}/data-scope-fields`,
    );
}

export function getRoleArchiveDataScopes(roleId: number) {
    return httpClient.get<RoleArchiveDataScopesDto>(
        `/authorization-roles/${roleId}/archive-data-scopes`,
    );
}

export function saveRoleArchiveDataScopes(roleId: number, scopeIds: number[]) {
    return httpClient.put<RoleArchiveDataScopesDto>(
        `/authorization-roles/${roleId}/archive-data-scopes`,
        { scopeIds },
    );
}

export function getUserArchiveDataScopes(userId: number) {
    return httpClient.get<UserArchiveDataScopesDto>(
        `/authorization-users/${userId}/archive-data-scopes`,
    );
}

export function saveUserArchiveDataScopes(userId: number, scopeIds: number[]) {
    return httpClient.put<UserArchiveDataScopesDto>(
        `/authorization-users/${userId}/archive-data-scopes`,
        { scopeIds },
    );
}

export function getDepartmentArchiveDataScopes(departmentId: number) {
    return httpClient.get<DepartmentArchiveDataScopesDto>(
        `/organization-departments/${departmentId}/archive-data-scopes`,
    );
}

export function saveDepartmentArchiveDataScopes(departmentId: number, scopeIds: number[]) {
    return httpClient.put<DepartmentArchiveDataScopesDto>(
        `/organization-departments/${departmentId}/archive-data-scopes`,
        { scopeIds },
    );
}

export function listAuthorizationRoles(enabled?: boolean, limit = 100, cursor?: string) {
    return httpClient.get<CursorPageResponse<AuthorizationRoleDto>>(
        pageUrl("/authorization-roles", { enabled, limit, cursor }),
    );
}

export function createAuthorizationRole(payload: CreateAuthorizationRoleRequest) {
    return httpClient.post<AuthorizationRoleDto>("/authorization-roles", payload);
}

export function updateAuthorizationRole(id: number, payload: UpdateAuthorizationRoleRequest) {
    return httpClient.patch<AuthorizationRoleDto>(`/authorization-roles/${id}`, payload, {
        headers: { "Content-Type": "application/merge-patch+json" },
    });
}

export function deleteAuthorizationRole(id: number) {
    return httpClient.delete<void>(`/authorization-roles/${id}`);
}
