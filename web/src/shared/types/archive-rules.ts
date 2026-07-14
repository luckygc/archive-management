import type { ArchiveLevel } from "./archive-metadata";

export type ArchiveRuleType =
    | "VALIDATION"
    | "DERIVATION"
    | "REFERENCE_CODE"
    | "RETENTION"
    | "ACCESS"
    | "QUALITY"
    | "TRANSFER"
    | "FILING"
    | "EXPORT";
export type ArchiveRuleStatus = "DRAFT" | "PUBLISHED";
export type ArchiveRuleEffectType =
    | "VALIDATION_ERROR"
    | "WARNING"
    | "SUGGEST_VALUE"
    | "DERIVED_VALUE"
    | "REQUIRE_REVIEW"
    | "REQUIRE_QUALITY_CHECK"
    | "DENY_ACCESS"
    | "MASK_FIELD"
    | "INCLUDE_IN_PACKAGE";
export type ArchiveRuleDecisionSeverity = "INFO" | "WARNING" | "ERROR";

export interface ArchiveRuleEffectRequest {
    effectType: ArchiveRuleEffectType;
    effectOrder?: number;
    effectParams?: Record<string, unknown>;
}

export interface ArchiveRuleRequest {
    schemeVersionId: number;
    ruleCode: string;
    ruleName: string;
    ruleType: ArchiveRuleType;
    triggerCode: string;
    scopeFondsCode?: string;
    scopeCategoryCode?: string;
    scopeObjectTypeId?: number;
    scopeArchiveLevel?: ArchiveLevel;
    scopeEventTypeId?: number;
    priority?: number;
    conditionJson: Record<string, unknown>;
    enabled?: boolean;
    effects: ArchiveRuleEffectRequest[];
}

export interface ArchiveRuleEffectDto {
    id: number;
    effectType: ArchiveRuleEffectType;
    effectOrder: number;
    effectParams: Record<string, unknown>;
}

export interface ArchiveRuleDto {
    id: number;
    schemeVersionId: number;
    ruleCode: string;
    ruleName: string;
    ruleType: ArchiveRuleType;
    triggerCode: string;
    status: ArchiveRuleStatus;
    enabled: boolean;
    priority: number;
    effects: ArchiveRuleEffectDto[];
}

export interface ExecuteArchiveRulesRequest {
    schemeVersionId: number;
    triggerCode: string;
    fondsCode?: string;
    categoryCode?: string;
    objectTypeCode?: string;
    archiveLevel?: ArchiveLevel;
    eventCode?: string;
    facts: Record<string, unknown>;
    includeSkipped: boolean;
    recordTrace: boolean;
    userId?: number;
}

export interface ArchiveRuleEffectDecision {
    effectType: ArchiveRuleEffectType;
    params: Record<string, unknown>;
}

export interface ArchiveRuleDecisionDto {
    ruleId?: number;
    ruleCode: string;
    ruleType: ArchiveRuleType;
    matched: boolean;
    effects: ArchiveRuleEffectDecision[];
    message?: string;
    severity: ArchiveRuleDecisionSeverity;
    blocking: boolean;
    skippedReason?: string;
}

export interface SearchArchiveRuleTracesRequest {
    schemeVersionId?: number;
    triggerCode?: string;
    objectTypeCode?: string;
    objectId?: number;
    ruleType?: ArchiveRuleType;
    limit?: number;
    userId?: number;
}

export interface ArchiveRuleTraceDto {
    id: number;
    schemeVersionId: number;
    triggerCode: string;
    objectTypeCode: string;
    objectId?: number;
    ruleId?: number;
    ruleCode?: string;
    ruleType?: ArchiveRuleType;
    matchedFlag: boolean;
    blockingFlag: boolean;
    effectJson: unknown;
    message?: string;
    severity?: ArchiveRuleDecisionSeverity;
    skippedReason?: string;
    createdBy?: number;
    createdAt: string;
}
