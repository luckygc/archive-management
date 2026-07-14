export type ArchiveGovernanceSchemeVersionStatus = "DRAFT" | "PUBLISHED" | "FROZEN" | "RETIRED";
export type ArchiveGovernanceScopeType = "GLOBAL" | "FONDS" | "CATEGORY";
export type ArchiveGovernanceBindingType =
    | "ONTOLOGY"
    | "RULE_SET"
    | "CLASSIFICATION_SCHEME"
    | "DESCRIPTION_PROFILE"
    | "REFERENCE_CODE_RULE";

export interface ArchiveGovernanceSchemeDto {
    id: number;
    schemeCode: string;
    schemeName: string;
    description?: string;
    enabled: boolean;
    sortOrder: number;
}

export interface ArchiveGovernanceSchemeRequest {
    schemeCode: string;
    schemeName: string;
    description?: string;
    enabled?: boolean;
    sortOrder?: number;
}

export interface ArchiveGovernanceSchemeVersionDto {
    id: number;
    schemeId: number;
    versionCode: string;
    versionDescription?: string;
    status: ArchiveGovernanceSchemeVersionStatus;
    publishedBy?: number;
    publishedAt?: string;
    frozenBy?: number;
    frozenAt?: string;
    retiredBy?: number;
    retiredAt?: string;
}

export interface ArchiveGovernanceSchemeVersionRequest {
    versionCode: string;
    versionDescription?: string;
}

export interface ArchiveGovernanceScopeRequest {
    scopeType?: ArchiveGovernanceScopeType;
    fondsCode?: string;
    categoryCode?: string;
    defaultFlag?: boolean;
}

export interface ArchiveGovernanceScopeDto {
    id: number;
    schemeVersionId: number;
    scopeType: ArchiveGovernanceScopeType;
    fondsCode?: string;
    categoryCode?: string;
    defaultFlag: boolean;
}

export interface ArchiveGovernanceBindingRequest {
    bindingType: ArchiveGovernanceBindingType;
    targetType?: string;
    targetId?: number;
    targetCode?: string;
    bindingOrder?: number;
}

export interface ArchiveGovernanceBindingDto {
    id: number;
    schemeVersionId: number;
    bindingType: ArchiveGovernanceBindingType;
    targetType?: string;
    targetId?: number;
    targetCode?: string;
    bindingOrder: number;
}
