export type ApprovalAction = "APPROVE" | "REJECT" | "WITHDRAW" | "TERMINATE";
export type ApprovalCandidateStrategy = "SPECIFIED_USERS";
export type ApprovalNodeType = "START" | "APPROVAL" | "EXCLUSIVE_GATEWAY" | "END";
export type ApprovalConditionOperator = "EQUALS" | "NOT_EQUALS" | "IN";
export type ApprovalInstanceStatus =
    | "RUNNING"
    | "APPROVED"
    | "REJECTED"
    | "WITHDRAWN"
    | "TERMINATED";
export type ApprovalTaskStatus = "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN" | "TERMINATED";

export interface ApprovalFlowNodeDto {
    nodeCode: string;
    nodeName: string;
    nodeType: ApprovalNodeType;
    x: number;
    y: number;
    candidateStrategy?: ApprovalCandidateStrategy;
    candidateUserIds: number[];
    allowedActions: ApprovalAction[];
}

export interface ApprovalFlowConditionDto {
    field: string;
    operator: ApprovalConditionOperator;
    values: string[];
}

export interface ApprovalFlowEdgeDto {
    edgeCode: string;
    sourceNodeCode: string;
    targetNodeCode: string;
    defaultFlow: boolean;
    condition?: ApprovalFlowConditionDto;
}

export interface ApprovalWorkflowGraphDto {
    nodes: ApprovalFlowNodeDto[];
    edges: ApprovalFlowEdgeDto[];
}

export interface ApprovalWorkflowDefinitionDto {
    id: number;
    definitionCode: string;
    definitionName: string;
    businessType: string;
    enabled: boolean;
    draftRevision: number;
    publishedVersionId?: number;
    graph: ApprovalWorkflowGraphDto;
    createdAt: string;
    updatedAt: string;
}

export interface ApprovalWorkflowDefinitionRequest {
    definitionCode?: string;
    definitionName: string;
    businessType: string;
    graph: ApprovalWorkflowGraphDto;
}

export interface ApprovalWorkflowDefinitionVersionDto {
    id: number;
    definitionId: number;
    versionNumber: number;
    graph: ApprovalWorkflowGraphDto;
    publishedBy: number;
    publishedAt: string;
}

export interface ApprovalWorkflowDefinitionOptionDto {
    id: number;
    definitionCode: string;
    definitionName: string;
    businessType: string;
}

export interface StartApprovalWorkflowInstanceRequest {
    definitionId: number;
    businessType: string;
    businessId: string;
    title: string;
    businessContext?: Record<string, string>;
}

export interface ApprovalWorkflowInstanceDto {
    id: number;
    definitionId: number;
    definitionVersionId: number;
    businessType: string;
    businessId: string;
    title: string;
    initiatorUserId: number;
    status: ApprovalInstanceStatus;
    currentNodeCode?: string;
    currentNodeName?: string;
    createdAt: string;
    completedAt?: string;
}

export interface ApprovalWorkflowTaskDto {
    id: string;
    nodeCode: string;
    nodeName: string;
    status: ApprovalTaskStatus;
    candidateUserIds: number[];
    assigneeUserId?: number;
    createdAt: string;
    completedAt?: string;
}

export interface ApprovalWorkflowOpinionDto {
    id: string;
    taskId?: string;
    action: ApprovalAction;
    operatorUserId: number;
    comment?: string;
    createdAt: string;
}

export interface ApprovalWorkflowInstanceDetailDto {
    instance: ApprovalWorkflowInstanceDto;
    tasks: ApprovalWorkflowTaskDto[];
    opinions: ApprovalWorkflowOpinionDto[];
}
