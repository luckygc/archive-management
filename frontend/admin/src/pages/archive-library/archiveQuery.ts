import type {
    ArchiveItemRelatedGroup,
    ArchiveItemWhere,
    SearchArchiveRecordsQuery,
} from "@/shared/types/archive-records";
import dayjs from "dayjs";

import type {
    ArchiveQueryFormValues,
    QueryConditionDraft,
    RelatedGroupDraft,
} from "./archiveQueryTypes";

export function toSearchQuery(values: ArchiveQueryFormValues): SearchArchiveRecordsQuery {
    return {
        categoryId: values.categoryId,
        fondsCode: trimToUndefined(values.fondsCode),
        keyword: trimToUndefined(values.keyword),
        limit: 100,
        relatedGroups: normalizeRelatedGroups(values.relatedGroups),
        where: normalizeWhere(values.conditions),
    };
}

function normalizeRelatedGroups(
    groups: RelatedGroupDraft[] | undefined,
): ArchiveItemRelatedGroup[] | undefined {
    const normalized: ArchiveItemRelatedGroup[] = [];
    for (const group of groups ?? []) {
        const categoryId = Number(group.categoryId);
        const where = normalizeWhere(group.conditions);
        if (Number.isFinite(categoryId) && where) {
            normalized.push({
                categoryId,
                direction: group.direction,
                where,
            });
        }
    }
    return normalized.length > 0 ? normalized : undefined;
}

function normalizeWhere(
    conditions: QueryConditionDraft[] | undefined,
): ArchiveItemWhere | undefined {
    const normalized = (conditions ?? [])
        .filter((condition) => condition.fieldCode)
        .map((condition) => ({
            fieldCode: condition.fieldCode as string,
            op: condition.op ?? "EQ",
            value: trimUnknown(condition.value),
            startValue: trimUnknown(condition.startValue),
            endValue: trimUnknown(condition.endValue),
        }));
    return normalized.length > 0 ? { conditions: normalized } : undefined;
}

function trimUnknown(value: unknown) {
    if (dayjs.isDayjs(value)) {
        return value.format("YYYY-MM-DD HH:mm:ss");
    }
    return typeof value === "string" ? trimToUndefined(value) : value;
}

function trimToUndefined(value: string | undefined) {
    const text = value?.trim();
    return text ? text : undefined;
}
