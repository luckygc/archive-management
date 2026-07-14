import { httpClient } from "@archive-management/frontend-core/api";

import type {
    ArchiveLineFieldResponse,
    ArchiveLineTableResponse,
    CreateArchiveLineFieldRequest,
    CreateArchiveLineTableRequest,
} from "../types/archive-line-tables";
import type { CollectionResponse } from "../types/pagination";

export function listArchiveLineTables(categoryId: number) {
    return httpClient.get<CollectionResponse<ArchiveLineTableResponse>>(
        `/api/v1/archive-categories/${categoryId}/item-line-tables`,
    );
}

export function createArchiveLineTable(categoryId: number, payload: CreateArchiveLineTableRequest) {
    return httpClient.post<ArchiveLineTableResponse>(
        `/api/v1/archive-categories/${categoryId}/item-line-tables`,
        payload,
    );
}

export function listArchiveLineFields(lineTableId: number) {
    return httpClient.get<CollectionResponse<ArchiveLineFieldResponse>>(
        `/api/v1/archive-item-line-tables/${lineTableId}/fields`,
    );
}

export function createArchiveLineField(
    lineTableId: number,
    payload: CreateArchiveLineFieldRequest,
) {
    return httpClient.post<ArchiveLineFieldResponse>(
        `/api/v1/archive-item-line-tables/${lineTableId}/fields`,
        payload,
    );
}

export function buildArchiveLineTable(lineTableId: number) {
    return httpClient.post<ArchiveLineTableResponse>(
        `/api/v1/archive-item-line-tables/${lineTableId}:build`,
    );
}
