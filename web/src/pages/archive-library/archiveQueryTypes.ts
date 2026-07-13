import type {
    ArchiveItemQueryOperator,
    ArchiveItemRelationDirection,
} from "@/shared/types/archive";

export interface QueryConditionDraft {
    fieldCode?: string;
    op?: ArchiveItemQueryOperator;
    value?: unknown;
    startValue?: unknown;
    endValue?: unknown;
}

export interface RelatedGroupDraft {
    categoryId?: number;
    direction?: ArchiveItemRelationDirection;
    conditions?: QueryConditionDraft[];
}

export interface ArchiveQueryFormValues {
    categoryId?: number;
    fondsCode?: string;
    keyword?: string;
    conditions?: QueryConditionDraft[];
    relatedGroups?: RelatedGroupDraft[];
}
