import type { SortingState } from "@tanstack/vue-table";

export type AmDataTableSortMode = "client" | "manual" | "none";

export type AmDataTableColumn<TData extends object = object> = {
    key: string;
    label: string;
    accessorKey?: (keyof TData & string) | string;
    accessor?: (row: TData) => unknown;
    width?: number | string;
    minWidth?: number | string;
    maxWidth?: number | string;
    align?: "left" | "center" | "right";
    sortable?: boolean;
    sortDescFirst?: boolean;
    fixed?: "left" | "right";
    ellipsis?: boolean;
    className?: string;
    headerClassName?: string;
};

export type { SortingState as AmDataTableSortingState };
