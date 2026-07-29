export interface IntakeOverviewDto {
    externalConnectionConfigured: boolean;
    status: "local_package_available";
    message: string;
}

export type ArchiveIntakePackageStatus =
    | "RECEIVED"
    | "CHECKING"
    | "PENDING_REVIEW"
    | "ACCEPTING"
    | "ACCEPTED"
    | "REJECTED"
    | "FAILED";

export type ArchiveIntakeValidationCategory =
    | "AUTHENTICITY"
    | "INTEGRITY"
    | "USABILITY"
    | "SECURITY";

export type ArchiveIntakeValidationOutcome = "PASSED" | "WARNING" | "MANUAL_REVIEW";

export interface ArchiveIntakePackageListItemResponse {
    id: number;
    formatProfile: string;
    packageCode?: string;
    originalFileName: string;
    contentLength: number;
    status: ArchiveIntakePackageStatus;
    itemCount: number;
    electronicFileCount: number;
    electronicFileBytes: number;
    validationPassedCount: number;
    validationWarningCount: number;
    validationManualCount: number;
    failureReason?: string;
    processingCompletedAt?: string;
    reviewedAt?: string;
    createdAt: string;
}

export interface ArchiveIntakePackageGeneratedItemResponse {
    archiveItemId: number;
    itemOrder: number;
    fondsCode: string;
    categoryCode: string;
    archiveNo?: string;
    electronicFileCount: number;
}

export interface ArchiveIntakeValidationResponse {
    code: string;
    category: ArchiveIntakeValidationCategory;
    outcome: ArchiveIntakeValidationOutcome;
    message: string;
}

export interface ArchiveIntakePackageDetailResponse extends ArchiveIntakePackageListItemResponse {
    sha256: string;
    receivedBy: number;
    reviewedBy?: number;
    reviewRemark?: string;
    sourceFixityConfirmed: boolean;
    contentReadabilityConfirmed: boolean;
    antivirusPassed: boolean;
    carrierSafetyConfirmed: boolean;
    handoverCompleted: boolean;
    processingStartedAt?: string;
    validations: ArchiveIntakeValidationResponse[];
    generatedItems: ArchiveIntakePackageGeneratedItemResponse[];
}

export interface ListArchiveIntakePackagesQuery {
    limit: number;
    cursor?: string;
}

export interface AcceptArchiveIntakePackageRequest {
    sourceFixityConfirmed: boolean;
    contentReadabilityConfirmed: boolean;
    antivirusPassed: boolean;
    carrierSafetyConfirmed: boolean;
    handoverCompleted: boolean;
    remark?: string;
}

export interface ArchiveIntakePackageDownloadLinkResponse {
    url: string;
    expiresAt: string;
}
