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
    ArchiveRecordOrderBy,
    CreateArchiveRecordRequest,
    ListArchiveItemAuditsRequest,
    ListArchiveItemRelationsQuery,
    PatchArchiveRecordRequest,
    ReassignArchiveRecordFondsRequest,
    SearchArchiveRecordsQuery,
    SearchArchiveRecordsRequest,
} from "../types/archive-records";
import type { CollectionResponse, CursorPageResponse } from "../types/pagination";
import { pageUrl, queryString } from "./query-string";

export function listArchiveRecords(params: {
    categoryId?: number;
    fondsCode?: string;
    limit?: number;
    cursor?: string;
}) {
    return httpClient.get<ArchiveRecordListDto>(pageUrl("/archive-items", params));
}

export function searchArchiveRecords(query: SearchArchiveRecordsQuery) {
    const request = archiveRecordSearchRequest("/archive-items:search", query);
    return httpClient.post<ArchiveRecordListDto>(request.url, request.body);
}

export function discoverArchiveRecords(query: SearchArchiveRecordsQuery) {
    const request = archiveRecordSearchRequest("/archive-items:discover", query);
    return httpClient.post<ArchiveRecordListDto>(request.url, request.body);
}

export function createArchiveRecord(payload: CreateArchiveRecordRequest) {
    return httpClient.post<ArchiveRecordDto>("/archive-items", payload);
}

export function getArchiveRecord(id: number, surface?: ArchiveLayoutSurface) {
    return httpClient.get<ArchiveRecordDetailDto>(
        `/archive-items/${id}${queryString({ surface })}`,
    );
}

export function updateArchiveRecord(id: number, payload: PatchArchiveRecordRequest) {
    return httpClient.patch<ArchiveRecordDetailDto>(`/archive-items/${id}`, payload, {
        headers: { "Content-Type": "application/merge-patch+json" },
    });
}

export function reassignArchiveRecordFonds(id: number, payload: ReassignArchiveRecordFondsRequest) {
    return httpClient.post<ArchiveRecordDetailDto>(`/archive-items/${id}:reassignFonds`, payload);
}

export function deleteArchiveRecord(id: number, reason?: string) {
    return httpClient.request<void>(`/archive-items/${id}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason }),
    });
}

export function lockArchiveRecord(id: number, reason?: string) {
    return httpClient.post<ArchiveRecordDto>(`/archive-items/${id}:lock`, { reason });
}

export function unlockArchiveRecord(id: number) {
    return httpClient.post<ArchiveRecordDto>(`/archive-items/${id}:unlock`);
}

interface ArchiveItemDownloadLinkResponse {
    url: string;
    expiresAt: string;
}

export async function downloadArchiveImportTemplate(categoryId: number): Promise<DownloadLink> {
    const response = await httpClient.post<ArchiveItemDownloadLinkResponse>(
        `/archive-categories/${categoryId}/archive-items:createImportTemplateDownloadLink`,
    );
    return httpClient.download(response.url);
}

export function importArchiveRecords(categoryId: number, file: File) {
    const formData = new FormData();
    formData.set("file", file);
    return httpClient.post<ArchiveImportResult>(
        `/archive-categories/${categoryId}/archive-items:import`,
        formData,
    );
}

export async function exportArchiveRecords(
    query: SearchArchiveRecordsQuery,
): Promise<DownloadLink> {
    const { limit: _limit, cursor: _cursor, orderBy, ...body } = query;
    const response = await httpClient.post<ArchiveItemDownloadLinkResponse>(
        `/archive-items:createExportDownloadLink${queryString({ sort: sortQuery(orderBy) })}`,
        compactObject(body),
    );
    return httpClient.download(response.url);
}

export function listArchiveItemElectronicFiles(archiveItemId: number) {
    return httpClient.get<CollectionResponse<ArchiveItemElectronicFileDto>>(
        `/archive-items/${archiveItemId}/electronic-files`,
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
        `/archive-items/${archiveItemId}/electronic-files`,
        formData,
    );
}

export function unbindArchiveItemElectronicFile(archiveItemId: number, electronicFileId: number) {
    return httpClient.delete<void>(
        `/archive-items/${archiveItemId}/electronic-files/${electronicFileId}`,
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
        `/archive-items/${archiveItemId}/electronic-files/${electronicFileId}:createDownloadLink`,
    );
    return httpClient.download(response.url);
}

export function listArchiveItemAudits(query: ListArchiveItemAuditsRequest) {
    return httpClient.get<CursorPageResponse<ArchiveItemAuditDto>>(
        pageUrl("/archive-item-audits", {
            archiveItemId: query.archiveItemId,
            fondsCode: query.fondsCode,
            categoryCode: query.categoryCode,
            operationType: query.operationType,
            operatedAfter: query.operatedAfter,
            operatedBefore: query.operatedBefore,
            limit: query.limit,
            cursor: query.cursor,
        }),
    );
}

export function listArchiveItemRelations(
    archiveItemId: number,
    query: ListArchiveItemRelationsQuery = {},
) {
    return httpClient.get<CursorPageResponse<ArchiveItemRelationResponse>>(
        pageUrl(`/archive-items/${archiveItemId}/relations`, {
            depth: query.depth,
            limit: query.limit,
            cursor: query.cursor,
        }),
    );
}

export function createArchiveItemRelation(archiveItemId: number, targetItemId: number) {
    return httpClient.post<ArchiveItemRelationResponse>(
        `/archive-items/${archiveItemId}/relations`,
        { targetItemId },
    );
}

export function deleteArchiveItemRelation(archiveItemId: number, relationId: number) {
    return httpClient.delete<void>(`/archive-items/${archiveItemId}/relations/${relationId}`);
}

function archiveRecordSearchRequest(
    path: string,
    query: SearchArchiveRecordsQuery,
): {
    url: string;
    body: SearchArchiveRecordsRequest;
} {
    const { limit, cursor, orderBy, ...body } = query;
    return {
        url: pageUrl(path, { limit, cursor, sort: sortQuery(orderBy) }),
        body: compactObject(body),
    };
}

function sortQuery(orderBy: ArchiveRecordOrderBy[] | undefined): string | undefined {
    return orderBy?.length
        ? orderBy
              .map(({ field, direction }) => `${direction === "ASC" ? "+" : "-"}${field}`)
              .join(",")
        : undefined;
}

function compactObject<T extends object>(value: T): T {
    return Object.fromEntries(
        Object.entries(value).filter(([, entryValue]) => entryValue !== undefined),
    ) as T;
}
