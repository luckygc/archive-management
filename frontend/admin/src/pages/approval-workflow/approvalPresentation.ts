import type {
    ApprovalAction,
    ApprovalInstanceStatus,
    ApprovalTaskStatus,
} from "@/shared/types/approval-workflow";

const instanceStatusLabels: Record<ApprovalInstanceStatus, string> = {
    RUNNING: "进行中",
    APPROVED: "已通过",
    REJECTED: "已驳回",
    WITHDRAWN: "已撤回",
    TERMINATED: "已终止",
};
const taskStatusLabels: Record<ApprovalTaskStatus, string> = {
    PENDING: "待办理",
    APPROVED: "已同意",
    REJECTED: "已驳回",
    WITHDRAWN: "已撤回",
    TERMINATED: "已终止",
};
const actionLabels: Record<ApprovalAction, string> = {
    APPROVE: "同意",
    REJECT: "驳回",
    WITHDRAW: "撤回",
    TERMINATE: "终止",
};

export function formatTime(value?: string) {
    return value ? value.replace("T", " ").slice(0, 19) : "-";
}

export function instanceStatusLabel(status: unknown) {
    return instanceStatusLabels[status as ApprovalInstanceStatus];
}

export function taskStatusLabel(status: unknown) {
    return taskStatusLabels[status as ApprovalTaskStatus];
}

export function actionLabel(action: unknown) {
    return actionLabels[action as ApprovalAction];
}

export function instanceTagType(status: unknown) {
    const value = status as ApprovalInstanceStatus;
    if (value === "APPROVED") return "success";
    if (value === "REJECTED" || value === "TERMINATED") return "danger";
    if (value === "RUNNING") return "primary";
    return "info";
}
