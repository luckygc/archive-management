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
import { pageUrl } from "./query-string";

export function listAuthenticationUsers(keyword?: string, limit = 100, cursor?: string) {
    return httpClient.get<CursorPageResponse<AuthenticationUserDto>>(
        pageUrl("/authentication-users", { keyword, limit, cursor }),
    );
}

export function listAuthenticationUserOptions(limit = 100, cursor?: string) {
    return httpClient.get<CursorPageResponse<AuthenticationUserOptionDto>>(
        pageUrl("/authentication-user-options", { limit, cursor }),
    );
}

export function createAuthenticationUser(payload: CreateAuthenticationUserRequest) {
    return httpClient.post<AuthenticationUserDto>("/authentication-users", payload);
}

export function getAuthenticationUser(id: number) {
    return httpClient.get<AuthenticationUserDetailDto>(`/authentication-users/${id}`);
}

export function updateAuthenticationUser(id: number, payload: UpdateAuthenticationUserRequest) {
    return httpClient.patch<AuthenticationUserDto>(`/authentication-users/${id}`, payload, {
        headers: { "Content-Type": "application/merge-patch+json" },
    });
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
