import type {
    ArchiveRuntimeActionType,
    ArchiveRuntimeDefinitionKind,
    ArchiveRuntimeTriggerPoint,
} from "@/shared/types/archive-rules";

export const triggerPoints: Array<{ value: ArchiveRuntimeTriggerPoint; label: string }> = [
    { value: "ITEM_BEFORE_CREATE", label: "条目创建前" },
    { value: "ITEM_BEFORE_UPDATE", label: "条目修改前" },
    { value: "ITEM_BEFORE_DELETE", label: "条目删除前" },
    { value: "VOLUME_BEFORE_CREATE", label: "案卷创建前" },
    { value: "VOLUME_BEFORE_ADD_ITEM", label: "条目入卷前" },
    { value: "FILE_BEFORE_UPLOAD", label: "电子文件上传前" },
    { value: "EXPORT_BEFORE_CREATE", label: "导出文件生成前" },
];
export const triggerLabels = Object.fromEntries(
    triggerPoints.map((item) => [item.value, item.label]),
);
export const assignmentTriggers = new Set<ArchiveRuntimeTriggerPoint>([
    "ITEM_BEFORE_CREATE",
    "ITEM_BEFORE_UPDATE",
    "VOLUME_BEFORE_CREATE",
    "VOLUME_BEFORE_ADD_ITEM",
]);

export function parseObject(value: string, label: string) {
    try {
        const parsed = JSON.parse(value || "{}");
        if (parsed == null || Array.isArray(parsed) || typeof parsed !== "object") {
            throw new Error();
        }
        return parsed as Record<string, unknown>;
    } catch {
        throw new Error(`${label} 必须是合法对象`);
    }
}

export function parseJsonValue(value: string) {
    try {
        return JSON.parse(value);
    } catch {
        return value;
    }
}

export function trim(value?: string) {
    return value?.trim() || undefined;
}

export function defaultEditor() {
    return {
        definitionKind: "CONSTRAINT" as ArchiveRuntimeDefinitionKind,
        definitionCode: "",
        definitionName: "",
        triggerPoint: "ITEM_BEFORE_CREATE" as ArchiveRuntimeTriggerPoint,
        scopeFondsCode: "",
        scopeCategoryCode: "",
        priority: 0,
        conditionJson: JSON.stringify({ field: "item.archiveNo", operator: "IS_EMPTY" }, null, 2),
        constraintAction: "REJECT" as "REJECT" | "WARN",
        constraintMessage: "档号不能为空",
        enabled: true,
        actions: [] as Array<{
            actionType: ArchiveRuntimeActionType;
            message: string;
            field: string;
            value: string;
        }>,
    };
}

export function defaultSimulation() {
    return {
        triggerPoint: "ITEM_BEFORE_CREATE" as ArchiveRuntimeTriggerPoint,
        fondsCode: "",
        categoryCode: "",
        candidateFacts: JSON.stringify(
            {
                "item.archiveNo": "A-001",
                "item.archiveYear": 2026,
                "context.userId": 1,
            },
            null,
            2,
        ),
    };
}
