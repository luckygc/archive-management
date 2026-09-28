import { httpClient } from "@archive-management/frontend-core/api";
import type {
    ArchiveRuntimeDefinitionDto,
    ArchiveRuntimeDefinitionRequest,
    ArchiveRuntimeExecutionRequest,
    ArchiveRuntimeExecutionResult,
    ArchiveRuntimeFieldCatalogDto,
    ArchiveRuntimeStatus,
    ArchiveRuntimeTraceDto,
    ArchiveRuntimeTriggerPoint,
    SearchArchiveRuntimeTracesQuery,
} from "../types/archive-rules";
import type { CollectionResponse, CursorPageResponse } from "../types/pagination";
import { queryString } from "./query-string";

export function listArchiveRuntimeDefinitions(status?: ArchiveRuntimeStatus) {
    return httpClient.get<CollectionResponse<ArchiveRuntimeDefinitionDto>>(
        `/archive-runtime-definitions${queryString({ status })}`,
    );
}

export function createArchiveRuntimeDefinition(payload: ArchiveRuntimeDefinitionRequest) {
    return httpClient.post<ArchiveRuntimeDefinitionDto>("/archive-runtime-definitions", payload);
}

export function updateArchiveRuntimeDefinition(
    id: number,
    payload: ArchiveRuntimeDefinitionRequest,
) {
    return httpClient.put<ArchiveRuntimeDefinitionDto>(
        `/archive-runtime-definitions/${id}`,
        payload,
    );
}

export function deleteArchiveRuntimeDefinition(id: number) {
    return httpClient.delete<void>(`/archive-runtime-definitions/${id}`);
}

export function publishArchiveRuntimeDefinition(id: number) {
    return httpClient.post<ArchiveRuntimeDefinitionDto>(
        `/archive-runtime-definitions/${id}:publish`,
    );
}

export function enableArchiveRuntimeDefinition(id: number) {
    return httpClient.post<ArchiveRuntimeDefinitionDto>(
        `/archive-runtime-definitions/${id}:enable`,
    );
}

export function disableArchiveRuntimeDefinition(id: number) {
    return httpClient.post<ArchiveRuntimeDefinitionDto>(
        `/archive-runtime-definitions/${id}:disable`,
    );
}

export function getArchiveRuntimeFields(params: {
    categoryCode?: string;
    triggerPoint: ArchiveRuntimeTriggerPoint;
}) {
    return httpClient.get<ArchiveRuntimeFieldCatalogDto>(
        `/archive-runtime-fields${queryString(params)}`,
    );
}

export function simulateArchiveRuntimeDefinitions(payload: ArchiveRuntimeExecutionRequest) {
    return httpClient.post<ArchiveRuntimeExecutionResult>(
        "/archive-runtime-definitions:simulate",
        payload,
    );
}

export function searchArchiveRuntimeTraces(query: SearchArchiveRuntimeTracesQuery) {
    const { limit, cursor, ...body } = query;
    return httpClient.post<CursorPageResponse<ArchiveRuntimeTraceDto>>(
        `/archive-runtime-traces:search${queryString({ limit, cursor })}`,
        body,
    );
}
