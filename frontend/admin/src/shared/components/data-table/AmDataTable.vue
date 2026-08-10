<script setup lang="ts" generic="TData extends object">
import {
    createExpandedRowModel,
    createSortedRowModel,
    rowExpandingFeature,
    rowSortingFeature,
    tableFeatures,
    useTable,
} from "@tanstack/vue-table";
import type { ColumnDef, ExpandedState, SortingState, Updater } from "@tanstack/vue-table";
import { computed, ref, watch } from "vue";
import type { CSSProperties } from "vue";

import type { AmDataTableColumn, AmDataTableSortMode } from "./types";

const tableFeatureSet = tableFeatures({
    rowSortingFeature,
    rowExpandingFeature,
    sortedRowModel: createSortedRowModel(),
    expandedRowModel: createExpandedRowModel(),
});

const props = withDefaults(
    defineProps<{
        data: TData[];
        columns: AmDataTableColumn<TData>[];
        rowKey?: (keyof TData & string) | ((row: TData, index: number) => string);
        childrenKey?: keyof TData & string;
        sortMode?: AmDataTableSortMode;
        sorting?: SortingState;
        loading?: boolean;
        emptyText?: string;
        loadingText?: string;
        size?: "large" | "default" | "small";
        bordered?: boolean;
        maxHeight?: number | string;
        defaultExpandAll?: boolean;
        highlightCurrentRow?: boolean;
        currentRowKey?: string | number;
    }>(),
    {
        sortMode: "client",
        loading: false,
        emptyText: "暂无数据",
        loadingText: "正在加载…",
        size: "default",
        bordered: false,
        defaultExpandAll: false,
        highlightCurrentRow: false,
    },
);

const emit = defineEmits<{
    sortingChange: [sorting: SortingState];
    rowClick: [row: TData];
    currentChange: [row: TData];
}>();

const internalSorting = ref<SortingState>(cloneSorting(props.sorting));
const expanded = ref<ExpandedState>(props.defaultExpandAll ? true : {});
const internalCurrentRowKey = ref<string | number | undefined>(props.currentRowKey);

watch(
    () => props.sorting,
    (sorting) => {
        if (sorting !== undefined) internalSorting.value = cloneSorting(sorting);
    },
    { deep: true },
);
watch(
    () => props.currentRowKey,
    (rowKey) => {
        internalCurrentRowKey.value = rowKey;
    },
);

const columnLookup = computed(
    () => new Map(props.columns.map((column) => [column.key, column] as const)),
);
const tanstackColumns = computed<ColumnDef<typeof tableFeatureSet, TData>[]>(() =>
    props.columns.map((column) => ({
        id: column.key,
        header: column.label,
        accessorFn: (row) =>
            column.accessor
                ? column.accessor(row)
                : readPath(row, column.accessorKey ?? column.key),
        enableSorting: props.sortMode !== "none" && column.sortable === true,
        enableMultiSort: true,
        sortDescFirst: column.sortDescFirst ?? false,
        sortFn: (rowA, rowB, columnId) =>
            compareValues(rowA.getValue(columnId), rowB.getValue(columnId)),
    })),
);

const table = useTable<typeof tableFeatureSet, TData>({
    features: tableFeatureSet,
    get data() {
        return props.data;
    },
    get columns() {
        return tanstackColumns.value;
    },
    initialState: {
        expanded: props.defaultExpandAll ? true : {},
    },
    state: {
        get sorting() {
            return internalSorting.value;
        },
        get expanded() {
            return expanded.value;
        },
    },
    onSortingChange: (updater) => {
        const next = applyUpdater(updater, internalSorting.value);
        internalSorting.value = cloneSorting(next);
        emit("sortingChange", cloneSorting(next));
    },
    onExpandedChange: (updater) => {
        expanded.value = applyUpdater(updater, expanded.value);
    },
    getRowId: (row, index) => resolveRowKey(row, index),
    getSubRows: (row) => readChildren(row),
    enableSorting: props.sortMode !== "none",
    enableMultiSort: true,
    manualSorting: props.sortMode !== "client",
    isMultiSortEvent: (event) => isMultiSortEvent(event),
});

const rows = computed(() => table.getRowModel().rows);
const containerStyle = computed<CSSProperties>(() => ({
    maxHeight: lengthValue(props.maxHeight),
}));

function applyUpdater<T>(updater: Updater<T>, current: T): T {
    return typeof updater === "function" ? (updater as (value: T) => T)(current) : updater;
}

function cloneSorting(sorting: SortingState | undefined): SortingState {
    return sorting?.map((item) => ({ ...item })) ?? [];
}

function readPath(row: TData, path: string): unknown {
    return path.split(".").reduce<unknown>((value, key) => {
        if (typeof value !== "object" || value === null) return undefined;
        return Reflect.get(value, key);
    }, row);
}

function readChildren(row: TData): TData[] | undefined {
    if (!props.childrenKey) return undefined;
    const children = readPath(row, props.childrenKey);
    return Array.isArray(children) ? (children as TData[]) : undefined;
}

function resolveRowKey(row: TData, index: number): string {
    if (typeof props.rowKey === "function") return props.rowKey(row, index);
    if (props.rowKey) {
        const value = readPath(row, props.rowKey);
        if (value !== undefined && value !== null) return String(value);
    }
    return String(index);
}

function compareValues(left: unknown, right: unknown): number {
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

function isMultiSortEvent(event: unknown): boolean {
    if (!(event instanceof MouseEvent) && !(event instanceof KeyboardEvent)) return false;
    return event.shiftKey || event.ctrlKey || event.metaKey;
}

function columnStyle(columnId: string): CSSProperties {
    const column = columnLookup.value.get(columnId);
    if (!column) return {};
    const style: CSSProperties = {
        width: lengthValue(column.width),
        minWidth: lengthValue(column.minWidth ?? column.width),
        maxWidth: lengthValue(column.maxWidth),
        textAlign: column.align,
    };
    if (column.fixed === "right") style.right = `${fixedOffset(columnId, "right")}px`;
    if (column.fixed === "left") style.left = `${fixedOffset(columnId, "left")}px`;
    return style;
}

function fixedOffset(columnId: string, side: "left" | "right"): number {
    const columns = side === "left" ? props.columns : [...props.columns].reverse();
    let offset = 0;
    for (const column of columns) {
        if (column.key === columnId) return offset;
        if (column.fixed === side) offset += numericWidth(column.width ?? column.minWidth);
    }
    return 0;
}

function numericWidth(value: number | string | undefined): number {
    if (typeof value === "number") return value;
    const parsed = Number.parseFloat(value ?? "");
    return Number.isFinite(parsed) ? parsed : 0;
}

function lengthValue(value: number | string | undefined): string | undefined {
    if (typeof value === "number") return `${value}px`;
    return value;
}

function cellClass(columnId: string): Array<string | undefined> {
    const column = columnLookup.value.get(columnId);
    return [
        column?.className,
        column?.ellipsis ? "am-data-table__cell--ellipsis" : undefined,
        column?.fixed ? `am-data-table__cell--fixed-${column.fixed}` : undefined,
    ];
}

function headerClass(columnId: string): Array<string | undefined> {
    const column = columnLookup.value.get(columnId);
    return [
        column?.headerClassName,
        column?.fixed ? `am-data-table__cell--fixed-${column.fixed}` : undefined,
    ];
}

function sortAriaLabel(columnId: string): string {
    const column = table.getColumn(columnId);
    const definition = columnLookup.value.get(columnId);
    if (!column || !definition) return "";
    const current = column.getIsSorted();
    const next = column.getNextSortingOrder();
    const currentText = current === "asc" ? "当前升序" : current === "desc" ? "当前降序" : "未排序";
    const nextText = next === "asc" ? "切换为升序" : next === "desc" ? "切换为降序" : "移除排序";
    return `${definition.label}，${currentText}；${nextText}，按住 Shift、Ctrl 或 Command 可追加多列排序`;
}

function sortTitle(columnId: string): string {
    const column = table.getColumn(columnId);
    const next = column?.getNextSortingOrder();
    const nextText = next === "asc" ? "升序" : next === "desc" ? "降序" : "取消排序";
    return `点击${nextText}；按住 Shift、Ctrl 或 Command 点击可追加多列排序`;
}

function selectRow(row: TData, rowId: string): void {
    internalCurrentRowKey.value = rowId;
    emit("rowClick", row);
    emit("currentChange", row);
}

function isCurrentRow(rowId: string): boolean {
    return props.highlightCurrentRow && String(internalCurrentRowKey.value ?? "") === rowId;
}
</script>

<template>
    <div
        class="am-data-table"
        :class="[`am-data-table--${props.size}`, { 'am-data-table--bordered': props.bordered }]"
        :aria-busy="props.loading"
    >
        <div class="am-data-table__viewport" :style="containerStyle">
            <table>
                <thead>
                    <tr v-for="headerGroup in table.getHeaderGroups()" :key="headerGroup.id">
                        <th
                            v-for="header in headerGroup.headers"
                            :key="header.id"
                            scope="col"
                            :class="headerClass(header.column.id)"
                            :style="columnStyle(header.column.id)"
                            :aria-sort="
                                header.column.getIsSorted() === 'asc'
                                    ? 'ascending'
                                    : header.column.getIsSorted() === 'desc'
                                      ? 'descending'
                                      : 'none'
                            "
                        >
                            <button
                                v-if="header.column.getCanSort()"
                                class="am-data-table__sort"
                                type="button"
                                :title="sortTitle(header.column.id)"
                                :aria-label="sortAriaLabel(header.column.id)"
                                @click="header.column.getToggleSortingHandler()?.($event)"
                            >
                                <span>{{ columnLookup.get(header.column.id)?.label }}</span>
                                <span
                                    v-if="header.column.getIsSorted()"
                                    class="am-data-table__sort-direction"
                                    aria-hidden="true"
                                    >{{ header.column.getIsSorted() === "asc" ? "↑" : "↓" }}</span
                                >
                                <span
                                    v-if="
                                        header.column.getSortIndex() >= 0 &&
                                        internalSorting.length > 1
                                    "
                                    class="am-data-table__sort-index"
                                    aria-hidden="true"
                                    >{{ header.column.getSortIndex() + 1 }}</span
                                >
                            </button>
                            <span v-else>{{ columnLookup.get(header.column.id)?.label }}</span>
                        </th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-if="rows.length === 0">
                        <td
                            class="am-data-table__empty"
                            :colspan="Math.max(props.columns.length, 1)"
                        >
                            {{ props.loading ? props.loadingText : props.emptyText }}
                        </td>
                    </tr>
                    <tr
                        v-for="row in rows"
                        v-else
                        :key="row.id"
                        :class="{ 'am-data-table__row--current': isCurrentRow(row.id) }"
                        @click="selectRow(row.original, row.id)"
                    >
                        <td
                            v-for="(cell, cellIndex) in row.getAllCells()"
                            :key="cell.id"
                            :class="cellClass(cell.column.id)"
                            :style="columnStyle(cell.column.id)"
                            :title="
                                columnLookup.get(cell.column.id)?.ellipsis
                                    ? String(cell.getValue() ?? '')
                                    : undefined
                            "
                        >
                            <div
                                v-if="cellIndex === 0 && row.getCanExpand()"
                                class="am-data-table__tree-cell"
                                :style="{ paddingLeft: `${row.depth * 20}px` }"
                            >
                                <button
                                    class="am-data-table__expand"
                                    type="button"
                                    :aria-label="`${row.getIsExpanded() ? '收起' : '展开'}${String(cell.getValue() ?? '当前行')}`"
                                    @click.stop="row.toggleExpanded()"
                                >
                                    {{ row.getIsExpanded() ? "−" : "+" }}
                                </button>
                                <slot
                                    :name="`cell-${cell.column.id}`"
                                    :row="row.original"
                                    :value="cell.getValue()"
                                    :index="row.index"
                                    >{{ cell.getValue() ?? "-" }}</slot
                                >
                            </div>
                            <slot
                                v-else
                                :name="`cell-${cell.column.id}`"
                                :row="row.original"
                                :value="cell.getValue()"
                                :index="row.index"
                                >{{ cell.getValue() ?? "-" }}</slot
                            >
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <div v-if="props.loading && rows.length > 0" class="am-data-table__loading" role="status">
            <span class="am-data-table__spinner" aria-hidden="true" />{{ props.loadingText }}
        </div>
    </div>
</template>

<style scoped>
.am-data-table {
    position: relative;
    min-width: 0;
    max-width: 100%;
    color: var(--el-text-color-primary);
    font-size: 14px;
}

.am-data-table__viewport {
    max-width: 100%;
    overflow: auto;
    border-top: 1px solid var(--el-border-color);
    border-bottom: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
    background: #ffffff;
}

table {
    width: max-content;
    min-width: 100%;
    border-spacing: 0;
    border-collapse: separate;
    text-align: left;
}

th,
td {
    padding: 11px 12px;
    border-bottom: 1px solid var(--el-border-color);
    background: #ffffff;
    line-height: 1.45;
    vertical-align: middle;
}

th {
    position: sticky;
    z-index: 2;
    top: 0;
    color: var(--el-text-color-regular);
    background: var(--el-fill-color-light);
    font-weight: 600;
    white-space: nowrap;
}

tbody tr:last-child td {
    border-bottom: 0;
}

tbody tr:hover td,
.am-data-table__row--current td {
    background: var(--el-color-primary-light-9, #ecf5ff);
}

.am-data-table--small th,
.am-data-table--small td {
    padding: 7px 10px;
}

.am-data-table--large th,
.am-data-table--large td {
    padding: 15px 14px;
}

.am-data-table--bordered th,
.am-data-table--bordered td {
    border-right: 1px solid var(--el-border-color);
}

.am-data-table--bordered th:first-child,
.am-data-table--bordered td:first-child {
    border-left: 1px solid var(--el-border-color);
}

.am-data-table__sort {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    width: 100%;
    min-height: 24px;
    padding: 0;
    border: 0;
    color: inherit;
    background: transparent;
    font: inherit;
    text-align: inherit;
    cursor: pointer;
}

.am-data-table__sort:hover,
.am-data-table__sort:focus-visible {
    color: var(--el-color-primary);
}

.am-data-table__sort:focus-visible,
.am-data-table__expand:focus-visible {
    border-radius: var(--el-border-radius-base);
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
}

.am-data-table__sort-direction {
    color: var(--el-color-primary);
    font-size: 16px;
}

.am-data-table__sort-index {
    display: inline-grid;
    min-width: 18px;
    height: 18px;
    padding: 0 4px;
    place-items: center;
    border-radius: 50%;
    color: #ffffff;
    background: var(--el-color-primary);
    font-size: 11px;
    line-height: 18px;
}

.am-data-table__cell--ellipsis {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.am-data-table__cell--fixed-left,
.am-data-table__cell--fixed-right {
    position: sticky;
    z-index: 1;
}

th.am-data-table__cell--fixed-left,
th.am-data-table__cell--fixed-right {
    z-index: 3;
}

.am-data-table__cell--fixed-left {
    box-shadow: 6px 0 8px -8px rgb(23 32 51 / 45%);
}

.am-data-table__cell--fixed-right {
    box-shadow: -6px 0 8px -8px rgb(23 32 51 / 45%);
}

.am-data-table__empty {
    height: 88px;
    color: var(--el-text-color-regular);
    text-align: center;
}

.am-data-table__loading {
    position: absolute;
    z-index: 5;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: var(--el-color-primary);
    background: rgb(255 255 255 / 78%);
}

.am-data-table__spinner {
    width: 18px;
    height: 18px;
    border: 2px solid var(--el-color-primary-light-7, #a0cfff);
    border-top-color: var(--el-color-primary);
    border-radius: 50%;
    animation: am-data-table-spin 0.8s linear infinite;
}

.am-data-table__tree-cell {
    display: flex;
    align-items: center;
    gap: 6px;
}

.am-data-table__expand {
    display: inline-grid;
    flex: 0 0 22px;
    width: 22px;
    height: 22px;
    padding: 0;
    place-items: center;
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
    color: var(--el-text-color-regular);
    background: #ffffff;
    cursor: pointer;
}

@keyframes am-data-table-spin {
    to {
        transform: rotate(360deg);
    }
}

@media (prefers-reduced-motion: reduce) {
    .am-data-table__spinner {
        animation: none;
    }
}
</style>
