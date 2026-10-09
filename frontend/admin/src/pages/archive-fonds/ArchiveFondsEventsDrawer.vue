<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";

import { listArchiveFondsEvents } from "@/shared/api/archive-metadata";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import { AmDataTable } from "@/shared/components/data-table";
import {
    isCursorFieldViolation,
    requestErrorMessage,
    withRequestTraceId,
} from "@/shared/requestError";
import type {
    ArchiveFondsDto,
    ArchiveFondsEventDto,
    ArchiveFondsEventType,
} from "@/shared/types/archive-metadata";
import type { CursorPageResponse } from "@/shared/types/pagination";

const open = defineModel<boolean>({ required: true });
const props = defineProps<{ fonds?: ArchiveFondsDto }>();
const page = ref<CursorPageResponse<ArchiveFondsEventDto>>();
const limit = ref(100);
const cursor = ref<string>();
const loading = ref(false);
const loadError = ref<string>();
let requestVersion = 0;
let disposed = false;
const eventTypeLabels: Record<ArchiveFondsEventType, string> = {
    NUMBER_ASSIGNED: "分配全宗号",
    CLOSED: "封闭",
    REOPENED: "重新开放",
};

watch(
    () => [open.value, props.fonds?.id] as const,
    ([isOpen, id]) => {
        invalidateRequest();
        page.value = undefined;
        cursor.value = undefined;
        loadError.value = undefined;
        if (isOpen && id !== undefined) void loadEvents();
    },
    { immediate: true },
);

onBeforeUnmount(() => {
    disposed = true;
    invalidateRequest();
});
function invalidateRequest() {
    requestVersion += 1;
    loading.value = false;
}

async function loadEvents(nextCursor = cursor.value, preserveError = false) {
    const id = props.fonds?.id;
    if (id === undefined || !open.value || disposed) return;
    const version = ++requestVersion;
    const isCurrent = () =>
        !disposed && open.value && props.fonds?.id === id && version === requestVersion;
    cursor.value = nextCursor;
    loading.value = true;
    if (!preserveError) loadError.value = undefined;
    try {
        const response = await listArchiveFondsEvents(id, limit.value, nextCursor);
        if (!isCurrent()) return;
        page.value = response;
        loadError.value = undefined;
    } catch (error) {
        if (!isCurrent()) return;
        if (nextCursor && isCursorFieldViolation(error)) {
            cursor.value = undefined;
            if (page.value)
                page.value = {
                    ...page.value,
                    self: undefined,
                    prev: undefined,
                    next: undefined,
                    first: undefined,
                };
            loadError.value = withRequestTraceId("数据已变化，将从第一页重新加载", error);
        } else loadError.value = requestErrorMessage(error, "全宗事件加载失败");
    } finally {
        if (isCurrent()) loading.value = false;
    }
}

function changeLimit(value: number) {
    limit.value = value;
    page.value = undefined;
    cursor.value = undefined;
    void loadEvents(undefined);
}

function formatDateTime(value: string | null) {
    return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—";
}
</script>

<template>
    <el-drawer
        v-model="open"
        :title="`${fonds?.fondsName ?? ''} · 全宗事件`"
        size="680px"
        destroy-on-close
        @close="invalidateRequest"
    >
        <RequestErrorState
            v-if="loadError"
            :message="loadError"
            :retrying="loading"
            retry-label="重试全宗事件"
            @retry="void loadEvents(cursor, true)"
        />
        <AmDataTable
            v-if="page || !loadError"
            :data="page?.items ?? []"
            :loading="loading"
            row-key="id"
            empty-text="暂无事件记录"
            :columns="[
                { key: 'eventType', label: '事件', width: 130 },
                { key: 'effectiveAt', label: '生效时间', width: 180 },
                { key: 'change', label: '变更', minWidth: 170 },
                { key: 'reason', label: '原因', minWidth: 180 },
            ]"
        >
            <template #cell-eventType="{ row }">{{ eventTypeLabels[row.eventType] }}</template>
            <template #cell-effectiveAt="{ row }">{{ formatDateTime(row.effectiveAt) }}</template>
            <template #cell-change="{ row }"
                >{{ row.previousValue || "—" }} → {{ row.currentValue || "—" }}</template
            >
        </AmDataTable>
        <div v-if="page" class="am-table-footer">
            <CursorPagination
                :limit="limit"
                :prev="page.prev"
                :next="page.next"
                :loading="loading"
                @page="loadEvents"
                @limit-change="changeLimit"
            />
        </div>
    </el-drawer>
</template>
