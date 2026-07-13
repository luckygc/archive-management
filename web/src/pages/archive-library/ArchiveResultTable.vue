<script setup lang="ts">
import { Lock, Setting } from "@element-plus/icons-vue";
import { computed, ref, watch } from "vue";

import type { ArchiveRecordListDto, ArchiveRecordOrderBy } from "@/shared/types/archive";
import { toArchiveRecordOrder } from "./archiveResultTable";

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

const baseColumns = computed(() => {
    const columns: Array<{
        key: string;
        label: string;
        prop?: string;
        width: number;
        sortable?: boolean;
        configurable?: boolean;
    }> = [
        { key: "archive_no", label: "档号", prop: "archive_no", width: 150, sortable: true },
        { key: "fonds_name", label: "全宗", prop: "fonds_name", width: 160, sortable: true },
        { key: "category_name", label: "分类", prop: "category_name", width: 150, sortable: true },
        { key: "archive_year", label: "年度", prop: "archive_year", width: 90, sortable: true },
        {
            key: "electronic_status",
            label: "状态",
            prop: "electronic_status",
            width: 120,
            sortable: true,
        },
    ];
    for (const field of props.result.fields.filter((item) => item.listVisible))
        columns.push({
            key: field.fieldCode,
            label: field.fieldName,
            prop: field.columnName,
            width: field.listWidth ?? 160,
            sortable: field.exactSearchable,
        });
    if (props.showLockColumn)
        columns.push({ key: "locked_flag", label: "锁定", prop: "locked_flag", width: 90 });
    if (props.showActions)
        columns.push({ key: "actions", label: "操作", width: 220, configurable: false });
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

function sortChange({
    prop,
    order,
}: {
    prop: string | null;
    order: "ascending" | "descending" | null;
}) {
    emit("orderChange", toArchiveRecordOrder(prop, order, props.result.fields));
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
        <el-table
            v-loading="loading"
            :data="result.items"
            :size="density"
            row-key="id"
            max-height="520"
            @sort-change="sortChange"
        >
            <el-table-column
                v-for="column in visibleColumns"
                :key="column.key"
                :label="column.label"
                :prop="column.prop"
                :width="column.width"
                :sortable="column.sortable ? 'custom' : false"
                :fixed="column.key === 'actions' ? 'right' : undefined"
            >
                <template v-if="column.key === 'locked_flag'" #default="{ row }"
                    ><el-icon v-if="row.locked_flag" aria-label="已锁定"><Lock /></el-icon
                ></template>
                <template v-else-if="column.key === 'actions'" #default="{ row }"
                    ><slot name="actions" :row="row"
                /></template>
            </el-table-column>
        </el-table>
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
