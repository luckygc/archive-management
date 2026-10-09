import type {
    ArchiveIntakePackageStatus,
    ArchiveIntakeValidationCategory,
    ArchiveIntakeValidationOutcome,
} from "@/shared/types/intake";

export function statusType(status: ArchiveIntakePackageStatus) {
    if (status === "ACCEPTED") return "success";
    if (status === "FAILED" || status === "REJECTED") return "danger";
    if (status === "CHECKING" || status === "ACCEPTING" || status === "PENDING_REVIEW")
        return "warning";
    return "info";
}

export function statusLabel(status: ArchiveIntakePackageStatus) {
    return {
        RECEIVED: "已接收",
        CHECKING: "自动检测中",
        PENDING_REVIEW: "待人工复核",
        ACCEPTING: "接收入库中",
        ACCEPTED: "已接收入馆藏",
        REJECTED: "已退回",
        FAILED: "自动检测失败",
    }[status];
}

export function validationCategoryLabel(category: ArchiveIntakeValidationCategory) {
    return {
        AUTHENTICITY: "真实性",
        INTEGRITY: "完整性",
        USABILITY: "可用性",
        SECURITY: "安全性",
    }[category];
}

export function validationOutcomeLabel(outcome: ArchiveIntakeValidationOutcome) {
    return {
        PASSED: "通过",
        WARNING: "提示",
        MANUAL_REVIEW: "需人工确认",
    }[outcome];
}

export function validationOutcomeType(outcome: ArchiveIntakeValidationOutcome) {
    if (outcome === "PASSED") return "success";
    return "warning";
}

export function formatSize(size: number) {
    return size < 1024
        ? `${size} B`
        : size < 1024 * 1024
          ? `${(size / 1024).toFixed(1)} KB`
          : `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function formatTime(value?: string) {
    return value ? value.replace("T", " ").slice(0, 19) : "—";
}

export function generatedItemsEmptyDescription(status: ArchiveIntakePackageStatus) {
    return status === "PENDING_REVIEW" ? "完成验收后生成正式馆藏档案" : "未生成正式馆藏档案";
}
