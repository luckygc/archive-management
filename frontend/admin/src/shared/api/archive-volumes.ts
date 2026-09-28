import { httpClient } from "@archive-management/frontend-core/api";

import type { CursorPageResponse } from "../types/pagination";
import type {
    ArchiveVolumeDetailResponse,
    ArchiveVolumeResponse,
    CreateArchiveVolumeRequest,
    ListArchiveVolumesQuery,
} from "../types/archive-volumes";
import { pageUrl } from "./query-string";

export function listArchiveVolumes(query: ListArchiveVolumesQuery) {
    return httpClient.get<CursorPageResponse<ArchiveVolumeResponse>>(
        pageUrl("/archive-volumes", { ...query }),
    );
}

export function createArchiveVolume(payload: CreateArchiveVolumeRequest) {
    return httpClient.post<ArchiveVolumeDetailResponse>("/archive-volumes", payload);
}

export function getArchiveVolume(volumeId: number) {
    return httpClient.get<ArchiveVolumeDetailResponse>(`/archive-volumes/${volumeId}`);
}

export function addArchiveItemToVolume(
    volumeId: number,
    itemId: number,
    displayOrder?: number,
): Promise<void> {
    return httpClient.post<void>(`/archive-volumes/${volumeId}:addItem`, {
        itemId,
        ...(displayOrder === undefined ? {} : { displayOrder }),
    });
}
