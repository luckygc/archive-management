import type { AmDataTableColumn } from "./types";
import type { SortingState, Updater } from "@tanstack/vue-table";

export function applyUpdater<T>(updater: Updater<T>, current: T): T {
    return typeof updater === "function" ? (updater as (value: T) => T)(current) : updater;
}

export function cloneSorting(sorting: SortingState | undefined): SortingState {
    return sorting?.map((item) => ({ ...item })) ?? [];
}

export function readPath(row: object, path: string): unknown {
    return path.split(".").reduce<unknown>((value, key) => {
        if (typeof value !== "object" || value === null) return undefined;
        return Reflect.get(value, key);
    }, row);
}

export function compareValues(left: unknown, right: unknown): number {
    if (left === right) return 0;
    if (left === undefined || left === null) return 1;
    if (right === undefined || right === null) return -1;
    if (typeof left === "number" && typeof right === "number") return left - right;
    if (typeof left === "boolean" && typeof right === "boolean")
        return Number(left) - Number(right);
    return String(left).localeCompare(String(right), "zh-CN", {
        numeric: true,
        sensitivity: "base",
    });
}

export function isMultiSortEvent(event: unknown): boolean {
    if (!(event instanceof MouseEvent) && !(event instanceof KeyboardEvent)) return false;
    return event.shiftKey || event.ctrlKey || event.metaKey;
}

export function numericWidth(value: number | string | undefined): number {
    if (typeof value === "number") return value;
    const parsed = Number.parseFloat(value ?? "");
    return Number.isFinite(parsed) ? parsed : 0;
}

export function lengthValue(value: number | string | undefined): string | undefined {
    if (typeof value === "number") return `${value}px`;
    return value;
}

export function fixedOffset<TData extends object>(
    columns: AmDataTableColumn<TData>[],
    columnId: string,
    side: "left" | "right",
): number {
    const ordered = side === "left" ? columns : [...columns].reverse();
    let offset = 0;
    for (const column of ordered) {
        if (column.key === columnId) return offset;
        if (column.fixed === side) offset += numericWidth(column.width ?? column.minWidth);
    }
    return 0;
}
