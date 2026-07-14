import type { ArchiveFieldDto } from "@/shared/types/archive-metadata";
import type {
    ArchiveDataScopeDto,
    ArchiveDataScopeDynamicFieldCondition,
    ArchiveDataScopeRequest,
} from "@/shared/types/authorization";

export type ArchiveDataScopeFormValues = Omit<ArchiveDataScopeRequest, "dynamicCondition"> & {
    fondsCodes: string[];
    categoryIds: number[];
    securityLevelIds: number[];
    retentionPeriodIds: number[];
    includeCategoryDescendants: boolean;
    dynamicFields: Array<Partial<ArchiveDataScopeDynamicFieldCondition> & { values: string[] }>;
};

export function emptyArchiveDataScopeForm(): ArchiveDataScopeFormValues {
    return {
        scopeCode: "",
        scopeName: "",
        scopeType: "CONDITIONAL",
        dimensions: [],
        enabled: true,
        fondsCodes: [],
        categoryIds: [],
        securityLevelIds: [],
        retentionPeriodIds: [],
        includeCategoryDescendants: false,
        dynamicFields: [],
    };
}

export function archiveDataScopeFieldOptions(fields?: ArchiveFieldDto[]) {
    return (fields ?? [])
        .filter((field) => field.fieldSource !== "BUILTIN")
        .map((field) => ({ label: field.fieldName, value: field.fieldCode }));
}

export function toArchiveDataScopeRequest(
    values: ArchiveDataScopeFormValues,
): ArchiveDataScopeRequest {
    if (values.scopeType === "ALL") {
        return {
            scopeCode: values.scopeCode,
            scopeName: values.scopeName,
            scopeType: values.scopeType,
            dimensions: [],
            dynamicCondition: undefined,
            enabled: values.enabled,
            description: values.description,
        };
    }
    return {
        scopeCode: values.scopeCode,
        scopeName: values.scopeName,
        scopeType: values.scopeType,
        dimensions: [
            ...values.fondsCodes.map((targetCode) => ({
                dimensionType: "FONDS" as const,
                targetCode,
                includeDescendants: false,
            })),
            ...values.categoryIds.map((targetId) => ({
                dimensionType: "CATEGORY" as const,
                targetId,
                includeDescendants: values.includeCategoryDescendants,
            })),
            ...values.securityLevelIds.map((targetId) => ({
                dimensionType: "SECURITY_LEVEL" as const,
                targetId,
                includeDescendants: false,
            })),
            ...values.retentionPeriodIds.map((targetId) => ({
                dimensionType: "RETENTION_PERIOD" as const,
                targetId,
                includeDescendants: false,
            })),
        ],
        dynamicCondition:
            values.dynamicFields.length > 0
                ? {
                      dynamicFields:
                          values.dynamicFields as ArchiveDataScopeDynamicFieldCondition[],
                  }
                : undefined,
        enabled: values.enabled,
        description: values.description,
    };
}

export function toArchiveDataScopeForm(row: ArchiveDataScopeDto): ArchiveDataScopeFormValues {
    return {
        scopeCode: row.scopeCode,
        scopeName: row.scopeName,
        scopeType: row.scopeType,
        enabled: row.enabled,
        description: row.description,
        dimensions: row.dimensions,
        fondsCodes: row.dimensions
            .filter((item) => item.dimensionType === "FONDS")
            .flatMap((item) => (item.targetCode ? [item.targetCode] : [])),
        categoryIds: row.dimensions
            .filter((item) => item.dimensionType === "CATEGORY")
            .flatMap((item) => (typeof item.targetId === "number" ? [item.targetId] : [])),
        securityLevelIds: row.dimensions
            .filter((item) => item.dimensionType === "SECURITY_LEVEL")
            .flatMap((item) => (typeof item.targetId === "number" ? [item.targetId] : [])),
        retentionPeriodIds: row.dimensions
            .filter((item) => item.dimensionType === "RETENTION_PERIOD")
            .flatMap((item) => (typeof item.targetId === "number" ? [item.targetId] : [])),
        includeCategoryDescendants: row.dimensions.some(
            (item) => item.dimensionType === "CATEGORY" && item.includeDescendants,
        ),
        dynamicFields: row.dynamicCondition?.dynamicFields.map((item) => ({ ...item })) ?? [],
    };
}

export function archiveDataScopeConditionText(value: unknown) {
    const row = value as ArchiveDataScopeDto;
    if (row.scopeType === "ALL") return "*";
    const dimensions = row.dimensions.map((item) => item.dimensionType).join("、");
    const dynamicCount = row.dynamicCondition?.dynamicFields.length ?? 0;
    return [dimensions, dynamicCount > 0 ? `动态字段 ${dynamicCount} 条` : undefined]
        .filter(Boolean)
        .join("；");
}
