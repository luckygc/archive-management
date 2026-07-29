import { httpClient } from "@archive-management/frontend-core/api";

import type {
    AcceptArchiveIntakePackageRequest,
    ArchiveIntakePackageDetailResponse,
    ArchiveIntakePackageDownloadLinkResponse,
    ArchiveIntakePackageListItemResponse,
    IntakeOverviewDto,
    ListArchiveIntakePackagesQuery,
} from "@/shared/types/intake";
import type { CursorPageResponse } from "@/shared/types/pagination";

import { queryString } from "./query-string";

export function getIntakeOverview() {
    return httpClient.get<IntakeOverviewDto>("/api/v1/intake");
}

export function listArchiveIntakePackages(query: ListArchiveIntakePackagesQuery) {
    return httpClient.get<CursorPageResponse<ArchiveIntakePackageListItemResponse>>(
        `/api/v1/archive-intake-packages${queryString({ ...query })}`,
    );
}

export function receiveArchiveIntakePackage(file: File) {
    const formData = new FormData();
    formData.set("file", file);
    return httpClient.post<ArchiveIntakePackageDetailResponse>(
        "/api/v1/archive-intake-packages",
        formData,
    );
}

export function getArchiveIntakePackage(id: number) {
    return httpClient.get<ArchiveIntakePackageDetailResponse>(
        `/api/v1/archive-intake-packages/${id}`,
    );
}

export function acceptArchiveIntakePackage(id: number, request: AcceptArchiveIntakePackageRequest) {
    return httpClient.post<ArchiveIntakePackageDetailResponse>(
        `/api/v1/archive-intake-packages/${id}:accept`,
        request,
    );
}

export function rejectArchiveIntakePackage(id: number, reason: string) {
    return httpClient.post<ArchiveIntakePackageDetailResponse>(
        `/api/v1/archive-intake-packages/${id}:reject`,
        { reason },
    );
}

export async function downloadArchiveIntakePackage(id: number) {
    const response = await httpClient.post<ArchiveIntakePackageDownloadLinkResponse>(
        `/api/v1/archive-intake-packages/${id}:createDownloadLink`,
    );
    return httpClient.download(response.url);
}
