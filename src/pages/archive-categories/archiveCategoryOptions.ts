import type {
    ArchiveFieldControl,
    ArchiveFieldType,
    ArchiveLayoutSurface,
    ArchiveLevel,
    ArchiveManagementMode,
} from "../../shared/types/archive";

export const fieldTypeOptions: Array<{ label: string; value: ArchiveFieldType }> = [
    { label: "文本", value: "text" },
    { label: "整数", value: "integer" },
    { label: "小数", value: "decimal" },
    { label: "日期", value: "date" },
    { label: "日期时间", value: "datetime" },
];

export const fieldControlLabels: Record<ArchiveFieldControl, string> = {
    input: "单行输入",
    textarea: "多行文本",
    number: "数字输入",
    date: "日期选择",
    datetime: "日期时间",
};

export const layoutSurfaceOptions: Array<{ label: string; value: ArchiveLayoutSurface }> = [
    { label: "表格", value: "table" },
    { label: "详情", value: "detail" },
    { label: "编辑", value: "edit" },
];

export const archiveLevelOptions: Array<{ label: string; value: ArchiveLevel }> = [
    { label: "卷内", value: "item" },
    { label: "案卷", value: "volume" },
];

export const managementModeOptions: Array<{ label: string; value: ArchiveManagementMode }> = [
    { label: "按条目管理", value: "item_only" },
    { label: "按案卷/卷内管理", value: "volume_item" },
];

export function fieldControlOptions(fieldType: ArchiveFieldType) {
    if (fieldType === "text") {
        return ["input", "textarea"] satisfies ArchiveFieldControl[];
    }
    if (fieldType === "integer" || fieldType === "decimal") {
        return ["number"] satisfies ArchiveFieldControl[];
    }
    if (fieldType === "date") {
        return ["date"] satisfies ArchiveFieldControl[];
    }
    return ["datetime"] satisfies ArchiveFieldControl[];
}

export function defaultFieldControl(fieldType: ArchiveFieldType): ArchiveFieldControl {
    return fieldControlOptions(fieldType)[0];
}
