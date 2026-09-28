import { httpClient } from "@archive-management/frontend-core/api";

import type {
    ArchiveLineFieldResponse,
    ArchiveLineTableResponse,
    ArchiveItemLineRowResponse,
    ArchiveItemLineTableDefinitionResponse,
    CreateArchiveItemLineRowRequest,
    CreateArchiveLineFieldRequest,
    CreateArchiveLineTableRequest,
    ListArchiveItemLineRowsQuery,
    PatchArchiveItemLineRowRequest,
} from "../types/archive-line-tables";
import type { CollectionResponse, CursorPageResponse } from "../types/pagination";
import { queryString } from "./query-string";

export function listArchiveLineTables(categoryId: number) {
    return httpClient.get<CollectionResponse<ArchiveLineTableResponse>>(
        `/archive-categories/${categoryId}/item-line-tables`,
    );
}

export function listArchiveItemLineTables(archiveItemId: number) {
    return httpClient.get<CollectionResponse<ArchiveItemLineTableDefinitionResponse>>(
        `/archive-items/${archiveItemId}/line-tables`,
    );
}

export function createArchiveLineTable(categoryId: number, payload: CreateArchiveLineTableRequest) {
    return httpClient.post<ArchiveLineTableResponse>(
        `/archive-categories/${categoryId}/item-line-tables`,
        payload,
    );
}

export function listArchiveLineFields(lineTableId: number) {
    return httpClient.get<CollectionResponse<ArchiveLineFieldResponse>>(
        `/archive-item-line-tables/${lineTableId}/fields`,
    );
}

export function createArchiveLineField(
    lineTableId: number,
    payload: CreateArchiveLineFieldRequest,
) {
    return httpClient.post<ArchiveLineFieldResponse>(
        `/archive-item-line-tables/${lineTableId}/fields`,
        payload,
    );
}

export function buildArchiveLineTable(lineTableId: number) {
    return httpClient.post<ArchiveLineTableResponse>(
        `/archive-item-line-tables/${lineTableId}:build`,
    );
}

export function listArchiveItemLineRows(
    archiveItemId: number,
    lineTableId: number,
    query: ListArchiveItemLineRowsQuery = {},
) {
    return httpClient.get<CursorPageResponse<ArchiveItemLineRowResponse>>(
        `/archive-items/${archiveItemId}/line-tables/${lineTableId}/rows${queryString({ limit: query.limit, cursor: query.cursor, requestTotal: query.cursor ? undefined : (query.requestTotal ?? true) })}`,
    );
}

export function createArchiveItemLineRow(
    archiveItemId: number,
    lineTableId: number,
    payload: CreateArchiveItemLineRowRequest,
) {
    return httpClient.post<ArchiveItemLineRowResponse>(
        `/archive-items/${archiveItemId}/line-tables/${lineTableId}/rows`,
        payload,
    );
}

export function patchArchiveItemLineRow(
    archiveItemId: number,
    lineTableId: number,
    rowId: number,
    payload: PatchArchiveItemLineRowRequest,
) {
    return httpClient.patch<ArchiveItemLineRowResponse>(
        `/archive-items/${archiveItemId}/line-tables/${lineTableId}/rows/${rowId}`,
        payload,
        { headers: { "Content-Type": "application/merge-patch+json" } },
    );
}

export function deleteArchiveItemLineRow(
    archiveItemId: number,
    lineTableId: number,
    rowId: number,
) {
    return httpClient.delete<void>(
        `/archive-items/${archiveItemId}/line-tables/${lineTableId}/rows/${rowId}`,
    );
}
