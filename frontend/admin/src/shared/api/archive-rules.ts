import { httpClient } from "@archive-management/frontend-core/api";
import type {
    ArchiveRuntimeDefinitionDto,
    ArchiveRuntimeDefinitionRequest,
    ArchiveRuntimeExecutionRequest,
    ArchiveRuntimeExecutionResult,
    ArchiveRuntimeFieldCatalogDto,
    ArchiveRuntimeSnapshot,
    ArchiveRuntimeSnapshotImportResult,
    ArchiveRuntimeSnapshotPreflightRequest,
    ArchiveRuntimeSnapshotPreflightResult,
    ArchiveRuntimeSnapshotRestoreResult,
    ArchiveRuntimeStatus,
    ArchiveRuntimeTraceDto,
    ArchiveRuntimeTriggerPoint,
    SearchArchiveRuntimeTracesQuery,
} from "../types/archive-rules";
import type { CollectionResponse, CursorPageResponse } from "../types/pagination";
import { queryString } from "./query-string";

export function listArchiveRuntimeDefinitions(
    schemeVersionId: number,
    status?: ArchiveRuntimeStatus,
) {
    return httpClient.get<CollectionResponse<ArchiveRuntimeDefinitionDto>>(
        `/api/v1/archive-runtime-definitions${queryString({ schemeVersionId, status })}`,
    );
}

export function createArchiveRuntimeDefinition(payload: ArchiveRuntimeDefinitionRequest) {
    return httpClient.post<ArchiveRuntimeDefinitionDto>(
        "/api/v1/archive-runtime-definitions",
        payload,
    );
}

export function updateArchiveRuntimeDefinition(
    id: number,
    payload: ArchiveRuntimeDefinitionRequest,
) {
    return httpClient.put<ArchiveRuntimeDefinitionDto>(
        `/api/v1/archive-runtime-definitions/${id}`,
        payload,
    );
}

export function deleteArchiveRuntimeDefinition(id: number) {
    return httpClient.delete<void>(`/api/v1/archive-runtime-definitions/${id}`);
}

export function publishArchiveRuntimeDefinition(id: number) {
    return httpClient.post<ArchiveRuntimeDefinitionDto>(
        `/api/v1/archive-runtime-definitions/${id}:publish`,
    );
}

export function enableArchiveRuntimeDefinition(id: number) {
    return httpClient.post<ArchiveRuntimeDefinitionDto>(
        `/api/v1/archive-runtime-definitions/${id}:enable`,
    );
}

export function disableArchiveRuntimeDefinition(id: number) {
    return httpClient.post<ArchiveRuntimeDefinitionDto>(
        `/api/v1/archive-runtime-definitions/${id}:disable`,
    );
}

export function getArchiveRuntimeFields(params: {
    schemeVersionId: number;
    categoryCode?: string;
    triggerPoint: ArchiveRuntimeTriggerPoint;
}) {
    return httpClient.get<ArchiveRuntimeFieldCatalogDto>(
        `/api/v1/archive-runtime-fields${queryString(params)}`,
    );
}

export function simulateArchiveRuntimeDefinitions(payload: ArchiveRuntimeExecutionRequest) {
    return httpClient.post<ArchiveRuntimeExecutionResult>(
        "/api/v1/archive-runtime-definitions:simulate",
        payload,
    );
}

export function searchArchiveRuntimeTraces(query: SearchArchiveRuntimeTracesQuery) {
    const { limit, cursor, requestTotal, ...body } = query;
    return httpClient.post<CursorPageResponse<ArchiveRuntimeTraceDto>>(
        `/api/v1/archive-runtime-traces:search${queryString({ limit, cursor, requestTotal: cursor ? undefined : (requestTotal ?? true) })}`,
        body,
    );
}

export function exportArchiveRuntimeSnapshot(schemeVersionId: number) {
    return httpClient.get<ArchiveRuntimeSnapshot>(
        `/api/v1/archive-governance-scheme-versions/${schemeVersionId}/runtime-snapshot`,
    );
}

export function preflightArchiveRuntimeSnapshot(payload: ArchiveRuntimeSnapshotPreflightRequest) {
    return httpClient.post<ArchiveRuntimeSnapshotPreflightResult>(
        "/api/v1/archive-runtime-snapshots:preflight",
        payload,
    );
}

export function importArchiveRuntimeSnapshot(payload: {
    preflight: ArchiveRuntimeSnapshotPreflightRequest;
    targetVersionCode: string;
    targetVersionDescription?: string;
}) {
    return httpClient.post<ArchiveRuntimeSnapshotImportResult>(
        "/api/v1/archive-runtime-snapshots:import",
        payload,
    );
}

export function restoreArchiveRuntimeSnapshot(
    schemeVersionId: number,
    payload: { preflight: ArchiveRuntimeSnapshotPreflightRequest },
) {
    return httpClient.post<ArchiveRuntimeSnapshotRestoreResult>(
        `/api/v1/archive-governance-scheme-versions/${schemeVersionId}:restore-runtime-snapshot`,
        payload,
    );
}
