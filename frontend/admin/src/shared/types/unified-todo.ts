export type UnifiedTodoStatus = "PENDING" | "COMPLETED" | "CANCELLED";

export interface UnifiedTodoDto {
    id: number;
    sourceType: string;
    sourceTaskId: string;
    businessType: string;
    businessId: string;
    title: string;
    nodeName?: string;
    assigneeUserId: number;
    status: UnifiedTodoStatus;
    sourcePath: string;
    createdAt: string;
    completedAt?: string;
}
