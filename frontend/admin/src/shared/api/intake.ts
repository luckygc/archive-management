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

import { pageUrl } from "./query-string";

export function getIntakeOverview() {
    return httpClient.get<IntakeOverviewDto>("/intake");
}

export function listArchiveIntakePackages(query: ListArchiveIntakePackagesQuery) {
    return httpClient.get<CursorPageResponse<ArchiveIntakePackageListItemResponse>>(
        pageUrl("/archive-intake-packages", { ...query }),
    );
}

export function receiveArchiveIntakePackage(file: File) {
    const formData = new FormData();
    formData.set("file", file);
    return httpClient.post<ArchiveIntakePackageDetailResponse>(
        "/archive-intake-packages",
        formData,
    );
}

export function getArchiveIntakePackage(id: number) {
    return httpClient.get<ArchiveIntakePackageDetailResponse>(`/archive-intake-packages/${id}`);
}

export function acceptArchiveIntakePackage(id: number, request: AcceptArchiveIntakePackageRequest) {
    return httpClient.post<ArchiveIntakePackageDetailResponse>(
        `/archive-intake-packages/${id}:accept`,
        request,
    );
}

export function rejectArchiveIntakePackage(id: number, reason: string) {
    return httpClient.post<ArchiveIntakePackageDetailResponse>(
        `/archive-intake-packages/${id}:reject`,
        { reason },
    );
}

export async function downloadArchiveIntakePackage(id: number) {
    const response = await httpClient.post<ArchiveIntakePackageDownloadLinkResponse>(
        `/archive-intake-packages/${id}:createDownloadLink`,
    );
    return httpClient.download(response.url);
}
