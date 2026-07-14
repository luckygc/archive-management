import { httpClient } from "@archive-management/frontend-core/api";
import type {
    ArchiveCategoryDto,
    ArchiveCategoryRequest,
    ArchiveClassificationSchemeDto,
    ArchiveClassificationSchemeRequest,
    ArchiveFieldDto,
    ArchiveFieldLayoutDto,
    ArchiveFieldLayoutRequest,
    ArchiveFieldRequest,
    ArchiveFondsCategoryScopeDto,
    ArchiveFondsCategoryScopeRequest,
    ArchiveFondsDto,
    ArchiveFondsRequest,
    ArchiveLayoutSurface,
    ArchiveLevel,
    ArchiveRetentionPeriodDto,
    ArchiveRetentionPeriodRequest,
    ArchiveSecurityLevelDto,
    ArchiveSecurityLevelRequest,
    ArchiveUniqueConstraintDto,
    ArchiveUniqueConstraintRequest,
} from "../types/archive-metadata";
import type { ArchiveRelatedFilterCategoryDto } from "../types/archive-records";
import type { CollectionResponse } from "../types/pagination";
import { queryString } from "./query-string";

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
