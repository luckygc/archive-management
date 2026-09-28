import { httpClient } from "@archive-management/frontend-core/api";
import type {
    ArchiveCategoryDto,
    ArchiveCategoryRequest,
    ArchiveFieldDto,
    ArchiveFieldLayoutDto,
    ArchiveFieldLayoutRequest,
    ArchiveFieldRequest,
    ArchiveFondsCategoryScopeDto,
    ArchiveFondsCategoryScopeRequest,
    ArchiveFondsDto,
    ArchiveFondsEventDto,
    ArchiveFondsLifecycleRequest,
    ArchiveFondsStatus,
    AssignArchiveFondsNumberRequest,
    CreateArchiveFondsRequest,
    ArchiveLayoutSurface,
    ArchiveLevel,
    ArchiveRetentionPeriodDto,
    ArchiveRetentionPeriodRequest,
    ArchiveSecurityLevelDto,
    ArchiveSecurityLevelRequest,
    ArchiveUniqueConstraintDto,
    ArchiveUniqueConstraintRequest,
    UpdateArchiveFondsRequest,
} from "../types/archive-metadata";
import type { ArchiveRelatedFilterCategoryDto } from "../types/archive-records";
import type { CollectionResponse } from "../types/pagination";
import { queryString } from "./query-string";

export function listArchiveFonds(status?: ArchiveFondsStatus) {
    return httpClient.get<CollectionResponse<ArchiveFondsDto>>(
        `/archive-fonds${queryString({ status })}`,
    );
}

export function createArchiveFonds(payload: CreateArchiveFondsRequest) {
    return httpClient.post<ArchiveFondsDto>("/archive-fonds", payload);
}

export function updateArchiveFonds(id: number, payload: UpdateArchiveFondsRequest) {
    return httpClient.patch<ArchiveFondsDto>(`/archive-fonds/${id}`, payload, {
        headers: { "Content-Type": "application/merge-patch+json" },
    });
}

export function assignArchiveFondsNumber(id: number, payload: AssignArchiveFondsNumberRequest) {
    return httpClient.post<ArchiveFondsDto>(`/archive-fonds/${id}:assignNumber`, payload);
}

export function closeArchiveFonds(id: number, payload: ArchiveFondsLifecycleRequest) {
    return httpClient.post<ArchiveFondsDto>(`/archive-fonds/${id}:close`, payload);
}

export function reopenArchiveFonds(id: number, payload: ArchiveFondsLifecycleRequest) {
    return httpClient.post<ArchiveFondsDto>(`/archive-fonds/${id}:reopen`, payload);
}

export function listArchiveFondsEvents(id: number) {
    return httpClient.get<CollectionResponse<ArchiveFondsEventDto>>(`/archive-fonds/${id}/events`);
}

export function listArchiveFondsCategoryScopes(fondsCode: string) {
    return httpClient.get<CollectionResponse<ArchiveFondsCategoryScopeDto>>(
        `/archive-fonds/${fondsCode}/category-scopes`,
    );
}

export function saveArchiveFondsCategoryScopes(
    fondsCode: string,
    payload: ArchiveFondsCategoryScopeRequest[],
) {
    return httpClient.put<CollectionResponse<ArchiveFondsCategoryScopeDto>>(
        `/archive-fonds/${fondsCode}/category-scopes`,
        payload,
    );
}

export function listArchiveCategoriesForFonds(fondsCode: string, enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveCategoryDto>>(
        `/archive-fonds/${fondsCode}/categories${queryString({ enabled })}`,
    );
}

export function listArchiveSecurityLevels(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveSecurityLevelDto>>(
        `/archive-security-levels${queryString({ enabled })}`,
    );
}

export function updateArchiveSecurityLevel(id: number, payload: ArchiveSecurityLevelRequest) {
    return httpClient.patch<ArchiveSecurityLevelDto>(`/archive-security-levels/${id}`, payload);
}

export function listArchiveRetentionPeriods(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveRetentionPeriodDto>>(
        `/archive-retention-periods${queryString({ enabled })}`,
    );
}

export function updateArchiveRetentionPeriod(id: number, payload: ArchiveRetentionPeriodRequest) {
    return httpClient.patch<ArchiveRetentionPeriodDto>(`/archive-retention-periods/${id}`, payload);
}

export function listArchiveCategories(enabled?: boolean) {
    return httpClient.get<CollectionResponse<ArchiveCategoryDto>>(
        `/archive-categories${queryString({ enabled })}`,
    );
}

export function listArchiveRelatedFilterCategories(categoryId: number) {
    return httpClient.get<CollectionResponse<ArchiveRelatedFilterCategoryDto>>(
        `/archive-categories/${categoryId}/related-filter-categories`,
    );
}

export function createArchiveCategory(payload: ArchiveCategoryRequest) {
    return httpClient.post<ArchiveCategoryDto>("/archive-categories", payload);
}

export function updateArchiveCategory(id: number, payload: ArchiveCategoryRequest) {
    return httpClient.patch<ArchiveCategoryDto>(`/archive-categories/${id}`, payload);
}

export function deleteArchiveCategory(id: number) {
    return httpClient.delete<void>(`/archive-categories/${id}`);
}

export function listArchiveFields(categoryId: number, archiveLevel?: ArchiveLevel) {
    return httpClient.get<CollectionResponse<ArchiveFieldDto>>(
        `/archive-categories/${categoryId}/fields${queryString({ archiveLevel })}`,
    );
}

export function createArchiveField(categoryId: number, payload: ArchiveFieldRequest) {
    return httpClient.post<ArchiveFieldDto>(`/archive-categories/${categoryId}/fields`, payload);
}

export function updateArchiveField(
    categoryId: number,
    fieldId: number,
    payload: ArchiveFieldRequest,
) {
    return httpClient.patch<ArchiveFieldDto>(
        `/archive-categories/${categoryId}/fields/${fieldId}`,
        payload,
    );
}

export function deleteArchiveField(categoryId: number, fieldId: number) {
    return httpClient.delete<void>(`/archive-categories/${categoryId}/fields/${fieldId}`);
}

export function getArchiveCategoryLayout(
    categoryId: number,
    surface: ArchiveLayoutSurface,
    archiveLevel?: ArchiveLevel,
) {
    return httpClient.get<ArchiveFieldLayoutDto>(
        `/archive-categories/${categoryId}/layouts/${surface}${queryString({ archiveLevel })}`,
    );
}

export function savePublicArchiveCategoryLayout(
    categoryId: number,
    surface: ArchiveLayoutSurface,
    payload: ArchiveFieldLayoutRequest,
    archiveLevel?: ArchiveLevel,
) {
    return httpClient.patch<ArchiveFieldLayoutDto>(
        `/archive-categories/${categoryId}/layouts/${surface}${queryString({ archiveLevel })}`,
        payload,
    );
}

export function buildArchiveCategoryTable(categoryId: number, archiveLevel?: ArchiveLevel) {
    return httpClient.post<ArchiveCategoryDto>(
        `/archive-categories/${categoryId}:buildTable${queryString({ archiveLevel })}`,
    );
}

export function listArchiveUniqueConstraints(categoryId: number) {
    return httpClient.get<CollectionResponse<ArchiveUniqueConstraintDto>>(
        `/archive-categories/${categoryId}/unique-constraints`,
    );
}

export function createArchiveUniqueConstraint(
    categoryId: number,
    payload: ArchiveUniqueConstraintRequest,
) {
    return httpClient.post<ArchiveUniqueConstraintDto>(
        `/archive-categories/${categoryId}/unique-constraints`,
        payload,
    );
}

export function updateArchiveUniqueConstraint(
    categoryId: number,
    constraintId: number,
    payload: ArchiveUniqueConstraintRequest,
) {
    return httpClient.patch<ArchiveUniqueConstraintDto>(
        `/archive-categories/${categoryId}/unique-constraints/${constraintId}`,
        payload,
    );
}

export function deleteArchiveUniqueConstraint(categoryId: number, constraintId: number) {
    return httpClient.delete<void>(
        `/archive-categories/${categoryId}/unique-constraints/${constraintId}`,
    );
}
