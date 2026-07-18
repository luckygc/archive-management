import { httpClient } from "@archive-management/frontend-core/api";

import type {
    ApprovalInstanceStatus,
    ApprovalWorkflowDefinitionDto,
    ApprovalWorkflowDefinitionOptionDto,
    ApprovalWorkflowDefinitionRequest,
    ApprovalWorkflowDefinitionVersionDto,
    ApprovalWorkflowInstanceDetailDto,
    ApprovalWorkflowInstanceDto,
    StartApprovalWorkflowInstanceRequest,
} from "../types/approval-workflow";
import type { CollectionResponse, CursorPageResponse } from "../types/pagination";
import { queryString } from "./query-string";

export function listApprovalWorkflowDefinitions(
    params: {
        enabled?: boolean;
        limit?: number;
        cursor?: string;
        requestTotal?: boolean;
    } = {},
) {
    return httpClient.get<CursorPageResponse<ApprovalWorkflowDefinitionDto>>(
        `/api/v1/approval-workflow-definitions${queryString({ limit: 100, ...params, requestTotal: params.cursor ? undefined : (params.requestTotal ?? true) })}`,
    );
}

export function listApprovalWorkflowDefinitionOptions() {
    return httpClient.get<CollectionResponse<ApprovalWorkflowDefinitionOptionDto>>(
        "/api/v1/approval-workflow-definition-options",
    );
}

export function createApprovalWorkflowDefinition(payload: ApprovalWorkflowDefinitionRequest) {
    return httpClient.post<ApprovalWorkflowDefinitionDto>(
        "/api/v1/approval-workflow-definitions",
        payload,
    );
}

export function updateApprovalWorkflowDefinition(
    id: number,
    payload: ApprovalWorkflowDefinitionRequest,
) {
    const { definitionName, businessType, graph } = payload;
    return httpClient.patch<ApprovalWorkflowDefinitionDto>(
        `/api/v1/approval-workflow-definitions/${id}`,
        { definitionName, businessType, graph },
    );
}

export function getApprovalWorkflowDefinition(id: number) {
    return httpClient.get<ApprovalWorkflowDefinitionDto>(
        `/api/v1/approval-workflow-definitions/${id}`,
    );
}

export function listApprovalWorkflowDefinitionVersions(
    id: number,
    params: { limit?: number; cursor?: string } = {},
) {
    return httpClient.get<CursorPageResponse<ApprovalWorkflowDefinitionVersionDto>>(
        `/api/v1/approval-workflow-definitions/${id}/versions${queryString({ limit: 100, ...params })}`,
    );
}

export function publishApprovalWorkflowDefinition(id: number) {
    return httpClient.post<ApprovalWorkflowDefinitionVersionDto>(
        `/api/v1/approval-workflow-definitions/${id}:publish`,
    );
}

export function setApprovalWorkflowDefinitionEnabled(id: number, enabled: boolean) {
    return httpClient.post<ApprovalWorkflowDefinitionDto>(
        `/api/v1/approval-workflow-definitions/${id}:${enabled ? "enable" : "disable"}`,
    );
}

export function startApprovalWorkflowInstance(payload: StartApprovalWorkflowInstanceRequest) {
    return httpClient.post<ApprovalWorkflowInstanceDto>(
        "/api/v1/approval-workflow-instances",
        payload,
    );
}

export function listMyApprovalWorkflowInstances(
    params: {
        status?: ApprovalInstanceStatus;
        limit?: number;
        cursor?: string;
        requestTotal?: boolean;
    } = {},
) {
    return httpClient.get<CursorPageResponse<ApprovalWorkflowInstanceDto>>(
        `/api/v1/approval-workflow-instances${queryString({ limit: 100, ...params, requestTotal: params.cursor ? undefined : (params.requestTotal ?? true) })}`,
    );
}

export function getApprovalWorkflowInstance(id: number) {
    return httpClient.get<ApprovalWorkflowInstanceDetailDto>(
        `/api/v1/approval-workflow-instances/${id}`,
    );
}

export function withdrawApprovalWorkflowInstance(id: number, comment?: string) {
    return httpClient.post<ApprovalWorkflowInstanceDetailDto>(
        `/api/v1/approval-workflow-instances/${id}:withdraw`,
        { comment },
    );
}

export function terminateApprovalWorkflowInstance(id: number, comment?: string) {
    return httpClient.post<ApprovalWorkflowInstanceDetailDto>(
        `/api/v1/approval-workflow-instances/${id}:terminate`,
        { comment },
    );
}

export function approveApprovalWorkflowTask(id: number, comment?: string) {
    return httpClient.post<ApprovalWorkflowInstanceDetailDto>(
        `/api/v1/approval-workflow-tasks/${id}:approve`,
        { comment },
    );
}

export function rejectApprovalWorkflowTask(id: number, comment?: string) {
    return httpClient.post<ApprovalWorkflowInstanceDetailDto>(
        `/api/v1/approval-workflow-tasks/${id}:reject`,
        { comment },
    );
}
