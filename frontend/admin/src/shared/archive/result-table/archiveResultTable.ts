import type { SortingState } from "@tanstack/vue-table";

import type { ArchiveRecordOrderBy } from "@/shared/types/archive-records";

export function toArchiveRecordOrder(sorting: SortingState): ArchiveRecordOrderBy[] {
    return sorting.map(({ id, desc }) => ({
        field: id,
        direction: desc ? "DESC" : "ASC",
    }));
}

export function toTableSorting(orderBy: ArchiveRecordOrderBy[]): SortingState {
    return orderBy.map(({ field, direction }) => ({
        id: field,
        desc: direction === "DESC",
    }));
}
