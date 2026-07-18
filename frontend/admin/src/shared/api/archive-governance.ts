import { httpClient } from "@archive-management/frontend-core/api";
import type {
    ArchiveGovernanceBindingDto,
    ArchiveGovernanceBindingRequest,
    ArchiveGovernanceSchemeDto,
    ArchiveGovernanceSchemeRequest,
    ArchiveGovernanceSchemeVersionDto,
    ArchiveGovernanceSchemeVersionRequest,
    ArchiveGovernanceScopeDto,
    ArchiveGovernanceScopeRequest,
} from "../types/archive-governance";
import type { CollectionResponse } from "../types/pagination";
import { queryString } from "./query-string";

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
