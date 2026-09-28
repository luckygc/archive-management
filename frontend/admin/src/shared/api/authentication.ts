import { httpClient } from "@archive-management/frontend-core/api";
import type {
    AuthenticationUserDetailDto,
    AuthenticationUserDto,
    AuthenticationUserOptionDto,
    CreateAuthenticationUserRequest,
    ResetPasswordRequest,
    RoleSummaryDto,
    SaveUserRolesRequest,
    UpdateAuthenticationUserRequest,
} from "../types/authentication";
import type { CollectionResponse, CursorPageResponse } from "../types/pagination";

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
        `/authentication-users?${params.toString()}`,
    );
}

export function listAuthenticationUserOptions(limit = 100, cursor?: string) {
    const params = new URLSearchParams({ limit: String(limit) });
    if (cursor) {
        params.set("cursor", cursor);
    }
    return httpClient.get<CursorPageResponse<AuthenticationUserOptionDto>>(
        `/authentication-user-options?${params.toString()}`,
    );
}

export function createAuthenticationUser(payload: CreateAuthenticationUserRequest) {
    return httpClient.post<AuthenticationUserDto>("/authentication-users", payload);
}

export function getAuthenticationUser(id: number) {
    return httpClient.get<AuthenticationUserDetailDto>(`/authentication-users/${id}`);
}

export function updateAuthenticationUser(id: number, payload: UpdateAuthenticationUserRequest) {
    return httpClient.patch<AuthenticationUserDto>(`/authentication-users/${id}`, payload);
}

export function resetAuthenticationUserPassword(id: number, payload: ResetPasswordRequest) {
    return httpClient.post<void>(`/authentication-users/${id}:resetPassword`, payload);
}

export function listAuthenticationUserRoles(id: number) {
    return httpClient.get<CollectionResponse<RoleSummaryDto>>(`/authentication-users/${id}/roles`);
}

export function saveAuthenticationUserRoles(id: number, payload: SaveUserRolesRequest) {
    return httpClient.put<CollectionResponse<RoleSummaryDto>>(
        `/authentication-users/${id}/roles`,
        payload,
    );
}
