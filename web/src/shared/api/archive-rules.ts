import { httpClient } from "@archive-management/frontend-core/api";
import type {
    ArchiveRuleDecisionDto,
    ArchiveRuleDto,
    ArchiveRuleRequest,
    ArchiveRuleStatus,
    ArchiveRuleTraceDto,
    ExecuteArchiveRulesRequest,
    SearchArchiveRuleTracesRequest,
} from "../types/archive-rules";
import type { CollectionResponse } from "../types/pagination";
import { queryString } from "./query-string";

export function listArchiveRules(schemeVersionId: number, status?: ArchiveRuleStatus) {
    return httpClient.get<CollectionResponse<ArchiveRuleDto>>(
        `/api/v1/archive-rules${queryString({ schemeVersionId, status })}`,
    );
}

export function createArchiveRule(payload: ArchiveRuleRequest) {
    return httpClient.post<ArchiveRuleDto>("/api/v1/archive-rules", payload);
}

export function publishArchiveRule(id: number) {
    return httpClient.post<ArchiveRuleDto>(`/api/v1/archive-rules/${id}:publish`);
}

export function enableArchiveRule(id: number) {
    return httpClient.post<ArchiveRuleDto>(`/api/v1/archive-rules/${id}:enable`);
}

export function disableArchiveRule(id: number) {
    return httpClient.post<ArchiveRuleDto>(`/api/v1/archive-rules/${id}:disable`);
}

export function executeArchiveRules(payload: ExecuteArchiveRulesRequest) {
    return httpClient.post<CollectionResponse<ArchiveRuleDecisionDto>>(
        "/api/v1/archive-rules:execute",
        payload,
    );
}

export function searchArchiveRuleTraces(payload: SearchArchiveRuleTracesRequest) {
    return httpClient.post<CollectionResponse<ArchiveRuleTraceDto>>(
        "/api/v1/archive-rule-traces:search",
        payload,
    );
}
