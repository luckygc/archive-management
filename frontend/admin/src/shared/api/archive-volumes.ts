import { httpClient } from "@archive-management/frontend-core/api";

import type { CursorPageResponse } from "../types/pagination";
import type {
    ArchiveVolumeDetailResponse,
    ArchiveVolumeResponse,
    CreateArchiveVolumeRequest,
    ListArchiveVolumesQuery,
} from "../types/archive-volumes";
import { queryString } from "./query-string";

export function listArchiveVolumes(query: ListArchiveVolumesQuery) {
    return httpClient.get<CursorPageResponse<ArchiveVolumeResponse>>(
        `/api/v1/archive-volumes${queryString({ ...query, requestTotal: query.cursor ? undefined : (query.requestTotal ?? true) })}`,
    );
}

export function createArchiveVolume(payload: CreateArchiveVolumeRequest) {
    return httpClient.post<ArchiveVolumeDetailResponse>("/api/v1/archive-volumes", payload);
}

export function getArchiveVolume(volumeId: number) {
    return httpClient.get<ArchiveVolumeDetailResponse>(`/api/v1/archive-volumes/${volumeId}`);
}

export function addArchiveItemToVolume(
    volumeId: number,
    itemId: number,
    displayOrder?: number,
): Promise<void> {
    return httpClient.post<void>(`/api/v1/archive-volumes/${volumeId}:addItem`, {
        itemId,
        ...(displayOrder === undefined ? {} : { displayOrder }),
    });
}
