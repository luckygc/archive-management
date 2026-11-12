import { httpClient } from "@archive-management/frontend-core/api";

import type { CursorPageResponse } from "../types/pagination";
import type {
    AccessTokenCallDto,
    AccessTokenDto,
    CreateAccessTokenRequest,
    CreateServiceAccountRequest,
    IssuedAccessTokenDto,
    ServiceAccountDto,
    ServiceAccountStatus,
    UpdateServiceAccountRequest,
} from "../types/service-account";
import { pageUrl } from "./query-string";

export function listServiceAccounts(status?: ServiceAccountStatus, limit = 100, cursor?: string) {
    return httpClient.get<CursorPageResponse<ServiceAccountDto>>(
        pageUrl("/service-accounts", { status, limit, cursor }),
    );
}

export function createServiceAccount(payload: CreateServiceAccountRequest) {
    return httpClient.post<ServiceAccountDto>("/service-accounts", payload);
}

export function updateServiceAccount(id: number, payload: UpdateServiceAccountRequest) {
    return httpClient.patch<ServiceAccountDto>(`/service-accounts/${id}`, payload, {
        headers: { "Content-Type": "application/merge-patch+json" },
    });
}

export function replaceServiceAccountRoles(id: number, roleIds: number[]) {
    return httpClient.put<ServiceAccountDto>(`/service-accounts/${id}/roles`, { roleIds });
}

export function deleteServiceAccount(id: number) {
    return httpClient.delete<void>(`/service-accounts/${id}`);
}

export function listServiceAccountTokens(serviceAccountId: number, limit = 100, cursor?: string) {
    return httpClient.get<CursorPageResponse<AccessTokenDto>>(
        pageUrl(`/service-accounts/${serviceAccountId}/access-tokens`, { limit, cursor }),
    );
}

export function issueServiceAccountToken(serviceAccountId: number, payload: CreateAccessTokenRequest) {
    return httpClient.post<IssuedAccessTokenDto>(
        `/service-accounts/${serviceAccountId}/access-tokens`,
        payload,
    );
}

export function revokeAccessToken(id: number) {
    return httpClient.post<void>(`/access-tokens/${id}:revoke`);
}

export function listAccessTokenCalls(params: {
    serviceAccountId?: number;
    tokenId?: number;
    limit?: number;
    cursor?: string;
}) {
    return httpClient.get<CursorPageResponse<AccessTokenCallDto>>(
        pageUrl("/access-token-calls", params),
    );
}
