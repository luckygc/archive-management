import { httpClient } from "@archive-management/frontend-core/api";
import type { DownloadLink } from "@archive-management/frontend-core/api";
import type { ArchiveLayoutSurface } from "../types/archive-metadata";
import type {
    ArchiveImportResult,
    ArchiveItemAuditDto,
    ArchiveItemElectronicFileDto,
    ArchiveItemElectronicFileUploadOptions,
    ArchiveItemRelationResponse,
    ArchiveRecordDetailDto,
    ArchiveRecordDto,
    ArchiveRecordListDto,
    CreateArchiveRecordRequest,
    ListArchiveItemAuditsRequest,
    ListArchiveItemRelationsQuery,
    SearchArchiveRecordsQuery,
    SearchArchiveRecordsRequest,
    UpdateArchiveRecordRequest,
} from "../types/archive-records";
import type { CollectionResponse, CursorPageResponse } from "../types/pagination";
import { queryString } from "./query-string";

export function listArchiveRecords(params: { categoryId?: number; fondsCode?: string }) {
    return httpClient.get<ArchiveRecordListDto>(`/api/v1/archive-items${queryString(params)}`);
}

export function searchArchiveRecords(query: SearchArchiveRecordsQuery) {
    const request = archiveRecordSearchRequest(query);
    return httpClient.post<ArchiveRecordListDto>(
        `/api/v1/archive-items:search${request.query}`,
        request.body,
    );
}

export function discoverArchiveRecords(query: SearchArchiveRecordsQuery) {
    const request = archiveRecordSearchRequest(query);
    return httpClient.post<ArchiveRecordListDto>(
        `/api/v1/archive-items:discover${request.query}`,
        request.body,
    );
}

export function createArchiveRecord(payload: CreateArchiveRecordRequest) {
    return httpClient.post<ArchiveRecordDto>("/api/v1/archive-items", payload);
}

export function getArchiveRecord(id: number, surface?: ArchiveLayoutSurface) {
    return httpClient.get<ArchiveRecordDetailDto>(
        `/api/v1/archive-items/${id}${queryString({ surface })}`,
    );
}

export function updateArchiveRecord(id: number, payload: UpdateArchiveRecordRequest) {
    return httpClient.patch<ArchiveRecordDetailDto>(`/api/v1/archive-items/${id}`, payload);
}

export function deleteArchiveRecord(id: number, reason?: string) {
    return httpClient.request<void>(`/api/v1/archive-items/${id}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason }),
    });
}

export function lockArchiveRecord(id: number, reason?: string) {
    return httpClient.post<ArchiveRecordDto>(`/api/v1/archive-items/${id}:lock`, { reason });
}

export function unlockArchiveRecord(id: number) {
    return httpClient.post<ArchiveRecordDto>(`/api/v1/archive-items/${id}:unlock`);
}

interface ArchiveItemDownloadLinkResponse {
    url: string;
    expiresAt: string;
}

export async function downloadArchiveImportTemplate(categoryId: number): Promise<DownloadLink> {
    const response = await httpClient.post<ArchiveItemDownloadLinkResponse>(
        `/api/v1/archive-categories/${categoryId}/archive-items:createImportTemplateDownloadLink`,
    );
    return httpClient.download(response.url);
}

export function importArchiveRecords(categoryId: number, file: File) {
    const formData = new FormData();
    formData.set("file", file);
    return httpClient.post<ArchiveImportResult>(
        `/api/v1/archive-categories/${categoryId}/archive-items:import`,
        formData,
    );
}

export async function exportArchiveRecords(
    query: SearchArchiveRecordsQuery,
): Promise<DownloadLink> {
    const response = await httpClient.post<ArchiveItemDownloadLinkResponse>(
        "/api/v1/archive-items:createExportDownloadLink",
        archiveRecordSearchRequest(query).body,
    );
    return httpClient.download(response.url);
}

export function listArchiveItemElectronicFiles(archiveItemId: number) {
    return httpClient.get<CollectionResponse<ArchiveItemElectronicFileDto>>(
        `/api/v1/archive-items/${archiveItemId}/electronic-files`,
    );
}

export function uploadArchiveItemElectronicFile(
    archiveItemId: number,
    file: File,
    options: ArchiveItemElectronicFileUploadOptions = {},
) {
    const formData = new FormData();
    formData.set("file", file);
    if (options.usageType) {
        formData.set("usageType", options.usageType);
    }
    if (typeof options.displayOrder === "number") {
        formData.set("displayOrder", String(options.displayOrder));
    }
    return httpClient.post<ArchiveItemElectronicFileDto>(
        `/api/v1/archive-items/${archiveItemId}/electronic-files`,
        formData,
    );
}

export function unbindArchiveItemElectronicFile(archiveItemId: number, electronicFileId: number) {
    return httpClient.delete<void>(
        `/api/v1/archive-items/${archiveItemId}/electronic-files/${electronicFileId}`,
    );
}

interface ArchiveItemElectronicFileDownloadLinkResponse {
    url: string;
    expiresAt: string;
}

export async function downloadArchiveItemElectronicFile(
    archiveItemId: number,
    electronicFileId: number,
): Promise<DownloadLink> {
    const response = await httpClient.post<ArchiveItemElectronicFileDownloadLinkResponse>(
        `/api/v1/archive-items/${archiveItemId}/electronic-files/${electronicFileId}:createDownloadLink`,
    );
    return httpClient.download(response.url);
}

export function listArchiveItemAudits(query: ListArchiveItemAuditsRequest) {
    return httpClient.get<CursorPageResponse<ArchiveItemAuditDto>>(
        `/api/v1/archive-item-audits${queryString({
            archiveItemId: query.archiveItemId,
            fondsCode: query.fondsCode,
            categoryCode: query.categoryCode,
            operationType: query.operationType,
            operatedAfter: query.operatedAfter,
            operatedBefore: query.operatedBefore,
            limit: query.limit,
            cursor: query.cursor,
            requestTotal: query.requestTotal,
        })}`,
    );
}

export function listArchiveItemRelations(
    archiveItemId: number,
    query: ListArchiveItemRelationsQuery = {},
) {
    return httpClient.get<CursorPageResponse<ArchiveItemRelationResponse>>(
        `/api/v1/archive-items/${archiveItemId}/relations${queryString({
            depth: query.depth,
            limit: query.limit,
            cursor: query.cursor,
        })}`,
    );
}

export function createArchiveItemRelation(archiveItemId: number, targetItemId: number) {
    return httpClient.post<ArchiveItemRelationResponse>(
        `/api/v1/archive-items/${archiveItemId}/relations`,
        { targetItemId },
    );
}

export function deleteArchiveItemRelation(archiveItemId: number, relationId: number) {
    return httpClient.delete<void>(
        `/api/v1/archive-items/${archiveItemId}/relations/${relationId}`,
    );
}

function archiveRecordSearchRequest(query: SearchArchiveRecordsQuery): {
    query: string;
    body: SearchArchiveRecordsRequest;
} {
    const { limit, cursor, requestTotal, ...body } = query;
    return {
        query: queryString({ limit, cursor, requestTotal }),
        body: compactObject(body),
    };
}

function compactObject<T extends object>(value: T): T {
    return Object.fromEntries(
        Object.entries(value).filter(([, entryValue]) => entryValue !== undefined),
    ) as T;
}
