<script setup lang="ts">
import { Lock, Setting } from "@element-plus/icons-vue";
import { computed, ref, watch } from "vue";

import { AmDataTable } from "@/shared/components/data-table";
import type { AmDataTableColumn, AmDataTableSortingState } from "@/shared/components/data-table";
import type { ArchiveRecordListDto, ArchiveRecordOrderBy } from "@/shared/types/archive-records";
import { toArchiveRecordOrder, toTableSorting } from "./archiveResultTable";

type RecordRow = Record<string, unknown>;
type TableDensity = "large" | "default" | "small";

const props = withDefaults(
    defineProps<{
        result: ArchiveRecordListDto;
        loading?: boolean;
        orderBy?: ArchiveRecordOrderBy[];
        showLockColumn?: boolean;
        showActions?: boolean;
    }>(),
    { loading: false, orderBy: () => [], showLockColumn: false, showActions: false },
);
const emit = defineEmits<{ orderChange: [orderBy: ArchiveRecordOrderBy[]] }>();
const density = ref<TableDensity>("default");
type ResultColumn = AmDataTableColumn<RecordRow> & { configurable?: boolean };

const baseColumns = computed(() => {
    const columns: ResultColumn[] = [
        {
            key: "archiveNo",
            label: "档号",
            accessorKey: "archive_no",
            width: 150,
            sortable: true,
        },
        {
            key: "fondsCode",
            label: "全宗",
            accessorKey: "fonds_name",
            width: 160,
            sortable: true,
        },
        {
            key: "categoryCode",
            label: "分类",
            accessorKey: "category_name",
            width: 150,
            sortable: true,
        },
        {
            key: "archiveYear",
            label: "年度",
            accessorKey: "archive_year",
            width: 90,
            sortable: true,
        },
    ];
    for (const field of props.result.fields.filter((item) => item.listVisible))
        columns.push({
            key: field.fieldCode,
            label: field.fieldName,
            accessorKey: field.columnName,
            width: field.listWidth ?? 160,
            sortable: field.exactSearchable,
        });
    if (props.showLockColumn)
        columns.push({ key: "locked_flag", label: "锁定", accessorKey: "locked_flag", width: 90 });
    if (props.showActions)
        columns.push({
            key: "actions",
            label: "操作",
            width: 220,
            fixed: "right",
            configurable: false,
        });
    return columns;
});
const visibleKeys = ref<string[]>([]);
watch(
    baseColumns,
    (columns) => {
        const available = new Set(columns.map((item) => item.key));
        const retained = visibleKeys.value.filter((key) => available.has(key));
        visibleKeys.value = [...new Set([...retained, ...columns.map((item) => item.key)])];
    },
    { immediate: true },
);
const visibleColumns = computed(() =>
    baseColumns.value.filter(
        (column) => column.configurable === false || visibleKeys.value.includes(column.key),
    ),
);
const tableSorting = computed(() => toTableSorting(props.orderBy));

function sortChange(sorting: AmDataTableSortingState) {
    emit("orderChange", toArchiveRecordOrder(sorting));
}
</script>

<template>
    <div class="am-result-table">
        <div class="am-result-table__toolbar">
            <el-popover placement="bottom-start" trigger="click" width="220">
                <template #reference><el-button :icon="Setting">列设置</el-button></template>
                <el-checkbox-group v-model="visibleKeys"
                    ><div
                        v-for="column in baseColumns.filter((item) => item.configurable !== false)"
                        :key="column.key"
                    >
                        <el-checkbox :label="column.label" :value="column.key" /></div
                ></el-checkbox-group>
            </el-popover>
            <el-segmented
                v-model="density"
                aria-label="表格密度"
                :options="[
                    { label: '宽松', value: 'large' },
                    { label: '默认', value: 'default' },
                    { label: '紧凑', value: 'small' },
                ]"
            />
        </div>
        <AmDataTable
            :data="result.items"
            :columns="visibleColumns"
            :size="density"
            :loading="loading"
            :sorting="tableSorting"
            sort-mode="manual"
            row-key="id"
            :max-height="520"
            @sorting-change="sortChange"
        >
            <template #cell-locked_flag="{ row }"
                ><el-icon v-if="row.locked_flag" aria-label="已锁定"><Lock /></el-icon
            ></template>
            <template #cell-actions="{ row }"><slot name="actions" :row="row" /></template>
        </AmDataTable>
    </div>
</template>

<style scoped>
.am-result-table__toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
}
</style>
