<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";

import { errorMessage } from "@archive-management/frontend-core/api";

import { listArchiveCategories, listArchiveFonds } from "@/shared/api/archive-metadata";
import { listArchiveVolumes } from "@/shared/api/archive-volumes";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import {
    isCursorFieldViolation,
    requestErrorMessage,
    withRequestTraceId,
} from "@/shared/requestError";
import type { ArchiveCategoryDto, ArchiveFondsDto } from "@/shared/types/archive-metadata";
import type { CursorPageResponse } from "@/shared/types/pagination";
import type {
    ArchiveVolumeResponse,
    ListArchiveVolumesQuery,
} from "@/shared/types/archive-volumes";
import { usePermissionStore } from "@/stores/permissionStore";

import ArchiveVolumeEditorDrawer from "./ArchiveVolumeEditorDrawer.vue";
import ArchiveVolumeItemsDrawer from "./ArchiveVolumeItemsDrawer.vue";

type VolumeListRequest = ListArchiveVolumesQuery;

const permissionStore = usePermissionStore();
const canCreate = computed(() => permissionStore.has("archive:item:create"));
const fonds = ref<ArchiveFondsDto[]>([]);
const categories = ref<ArchiveCategoryDto[]>([]);
const draft = reactive({ fondsCode: "", categoryCode: "" });
const committed = ref<{ fondsCode?: string; categoryCode?: string }>({});
const result = ref<CursorPageResponse<ArchiveVolumeResponse>>();
const limit = ref(100);
const cursor = ref<string>();
const loading = ref(false);
const loadError = ref<string>();
const editorState = ref<{ mode: "create" | "detail"; volumeId?: number }>();
const itemsVolume = ref<ArchiveVolumeResponse>();
let requestVersion = 0;
let disposed = false;
let failedRequest: VolumeListRequest | undefined;
let retryInFlight: Promise<void> | undefined;

onMounted(() => {
    void loadReferences();
    void execute();
});

onBeforeUnmount(() => {
    disposed = true;
    requestVersion += 1;
    loading.value = false;
});

async function loadReferences() {
    try {
        const [fondsResponse, categoryResponse] = await Promise.all([
            listArchiveFonds(true),
            listArchiveCategories(true),
        ]);
        if (disposed) return;
        fonds.value = fondsResponse.items;
        categories.value = categoryResponse.items.filter(
            (item) => item.managementMode === "VOLUME_ITEM",
        );
    } catch (error) {
        ElMessage.error(errorMessage(error, "加载案卷筛选项失败"));
    }
}

function execute(nextCursor = cursor.value) {
    const query: ListArchiveVolumesQuery = {
        ...committed.value,
        limit: limit.value,
        cursor: nextCursor,
    };
    return executeRequest(query);
}

async function executeRequest(query: VolumeListRequest, preserveError = false) {
    const version = ++requestVersion;
    cursor.value = query.cursor;
    loading.value = true;
    if (!preserveError) {
        loadError.value = undefined;
        failedRequest = undefined;
        retryInFlight = undefined;
    }
    try {
        const response = await listArchiveVolumes(query);
        if (!disposed && version === requestVersion) {
            result.value = response;
            loadError.value = undefined;
            failedRequest = undefined;
        }
    } catch (error) {
        if (!disposed && version === requestVersion) {
            const cursorInvalid = Boolean(query.cursor) && isCursorFieldViolation(error);
            failedRequest = cursorInvalid ? { ...query, cursor: undefined } : query;
            if (cursorInvalid) {
                cursor.value = undefined;
                loadError.value = withRequestTraceId("数据已变化，将从第一页重新加载", error);
            } else loadError.value = requestErrorMessage(error, "加载案卷失败");
        }
    } finally {
        if (!disposed && version === requestVersion) loading.value = false;
    }
}

function submit() {
    committed.value = {
        fondsCode: draft.fondsCode || undefined,
        categoryCode: draft.categoryCode || undefined,
    };
    clearCursors();
    void execute(undefined);
}

function reset() {
    draft.fondsCode = "";
    draft.categoryCode = "";
    committed.value = {};
    clearCursors();
    void execute(undefined);
}

function page(nextCursor: string) {
    void execute(nextCursor);
}

function limitChange(nextLimit: number) {
    limit.value = nextLimit;
    clearCursors();
    void execute(undefined);
}

function clearCursors() {
    cursor.value = undefined;
    if (!result.value) return;
    result.value = {
        ...result.value,
        self: undefined,
        prev: undefined,
        next: undefined,
        first: undefined,
    };
}

function refresh() {
    if (retryInFlight) return retryInFlight;
    if (failedRequest) {
        const promise = executeRequest({ ...failedRequest }, true);
        retryInFlight = promise;
        const clearRetry = () => {
            if (retryInFlight === promise) retryInFlight = undefined;
        };
        void promise.then(clearRetry, clearRetry);
        return promise;
    }
    return execute(cursor.value);
}

function created() {
    editorState.value = undefined;
    void refresh();
}

function categoryId(volume: ArchiveVolumeResponse) {
    return categories.value.find((item) => item.categoryCode === volume.categoryCode)?.id;
}

function openItems(row: unknown) {
    itemsVolume.value = row as ArchiveVolumeResponse;
}

function formatTime(value: string) {
    return value ? value.replace("T", " ").slice(0, 19) : "-";
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>案卷管理</h1>
            <div>
                <el-button :loading="loading" @click="refresh">刷新</el-button>
                <el-button
                    type="primary"
                    :disabled="!canCreate"
                    @click="editorState = { mode: 'create' }"
                    >新建案卷</el-button
                >
            </div>
        </div>
        <el-card class="am-page__filter" shadow="never">
            <el-form :model="draft" inline @submit.prevent="submit">
                <el-form-item label="全宗">
                    <el-select
                        v-model="draft.fondsCode"
                        aria-label="全宗"
                        clearable
                        filterable
                        placeholder="全部全宗"
                    >
                        <el-option
                            v-for="item in fonds"
                            :key="item.fondsCode"
                            :label="`${item.fondsCode} ${item.fondsName}`"
                            :value="item.fondsCode"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="档案分类">
                    <el-select
                        v-model="draft.categoryCode"
                        aria-label="档案分类"
                        clearable
                        filterable
                        placeholder="全部分类"
                    >
                        <el-option
                            v-for="item in categories"
                            :key="item.id"
                            :label="`${item.categoryCode} ${item.categoryName}`"
                            :value="item.categoryCode"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" native-type="submit">查询</el-button>
                    <el-button @click="reset">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>
        <el-card class="am-page__result" shadow="never">
            <RequestErrorState
                v-if="loadError"
                :message="loadError"
                :retrying="loading"
                @retry="refresh"
            />
            <el-table v-loading="loading" :data="result?.items || []" row-key="id">
                <el-table-column label="档号" prop="archiveNo" min-width="160">
                    <template #default="{ row }">{{ row.archiveNo || "-" }}</template>
                </el-table-column>
                <el-table-column label="全宗" min-width="180">
                    <template #default="{ row }">{{ row.fondsCode }} {{ row.fondsName }}</template>
                </el-table-column>
                <el-table-column label="档案分类" min-width="180">
                    <template #default="{ row }">
                        {{ row.categoryCode }} {{ row.categoryName }}
                    </template>
                </el-table-column>
                <el-table-column label="年度" prop="archiveYear" width="90" />
                <el-table-column label="电子状态" prop="electronicStatus" width="110" />
                <el-table-column label="创建时间" min-width="170">
                    <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" fixed="right" width="210">
                    <template #default="{ row }">
                        <el-button
                            link
                            type="primary"
                            @click="editorState = { mode: 'detail', volumeId: row.id }"
                        >
                            查看详情
                        </el-button>
                        <el-button link type="primary" @click="openItems(row)">
                            查看卷内档案
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>
            <div class="am-table-footer">
                <CursorPagination
                    :limit="limit"
                    :prev="result?.prev"
                    :next="result?.next"
                    :loading="loading"
                    @page="page"
                    @limit-change="limitChange"
                />
            </div>
        </el-card>
        <ArchiveVolumeEditorDrawer
            :state="editorState"
            :categories="categories"
            :fonds="fonds"
            @close="editorState = undefined"
            @created="created"
        />
        <ArchiveVolumeItemsDrawer
            :volume="itemsVolume"
            :category-id="itemsVolume ? categoryId(itemsVolume) : undefined"
            @close="itemsVolume = undefined"
        />
    </section>
</template>
