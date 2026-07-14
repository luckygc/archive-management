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
import { queryString } from "./query-string";

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
