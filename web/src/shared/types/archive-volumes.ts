import type { ArchiveElectronicStatus } from "./archive-records";

export interface ArchiveVolumeResponse {
    id: number;
    fondsCode: string;
    fondsName: string;
    categoryCode: string;
    categoryName: string;
    archiveNo?: string;
    electronicStatus: ArchiveElectronicStatus;
    archiveYear: number;
    lockedFlag: boolean;
    lockReason?: string;
    lockedBy?: number;
    lockedAt?: string;
    createdAt: string;
}

export type ArchiveVolumeDetailResponse = Omit<ArchiveVolumeResponse, "createdAt">;

export interface ListArchiveVolumesQuery {
    fondsCode?: string;
    categoryCode?: string;
    limit?: number;
    cursor?: string;
    requestTotal?: boolean;
}

export interface CreateArchiveVolumeRequest {
    categoryId: number;
    fondsCode: string;
    archiveNo?: string;
    archiveYear?: number;
    electronicStatus?: ArchiveElectronicStatus;
}
