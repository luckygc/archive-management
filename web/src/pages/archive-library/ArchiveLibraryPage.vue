<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";

import { discoverArchiveRecords } from "@/shared/api/archive-records";
import {
    listArchiveCategories,
    listArchiveFields,
    listArchiveRelatedFilterCategories,
} from "@/shared/api/archive-metadata";
import type { ArchiveCategoryDto, ArchiveFieldDto } from "@/shared/types/archive-metadata";
import type {
    ArchiveRecordListDto,
    ArchiveRecordOrderBy,
    ArchiveRelatedFilterCategoryDto,
    SearchArchiveRecordsQuery,
} from "@/shared/types/archive-records";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import {
    isCursorFieldViolation,
    requestErrorMessage,
    withRequestTraceId,
} from "@/shared/requestError";

import ArchiveAdvancedQueryPanel from "./ArchiveAdvancedQueryPanel.vue";
import type { ArchiveQueryFormValues } from "./archiveQueryTypes";
import ArchiveResultTable from "./ArchiveResultTable.vue";
import { toSearchQuery } from "./archiveQuery";

type DiscoverRequest = Parameters<typeof discoverArchiveRecords>[0];

const form = reactive<ArchiveQueryFormValues>({ conditions: [], relatedGroups: [] });
const categories = ref<ArchiveCategoryDto[]>([]);
const fields = ref<ArchiveFieldDto[]>([]);
const relatedCategories = ref<ArchiveRelatedFilterCategoryDto[]>([]);
const relatedFieldsByCategory = ref(new Map<number, ArchiveFieldDto[]>());
const result = ref<ArchiveRecordListDto>();
const committedQuery = ref<SearchArchiveRecordsQuery>();
const orderBy = ref<ArchiveRecordOrderBy[]>([]);
const limit = ref(100);
const cursor = ref<string>();
const loading = ref(false);
const loadError = ref<string>();
let categoryLoadVersion = 0;
let requestVersion = 0;
let disposed = false;
let failedRequest: DiscoverRequest | undefined;
let retryInFlight: Promise<void> | undefined;

onBeforeUnmount(() => {
    disposed = true;
    requestVersion += 1;
    categoryLoadVersion += 1;
    loading.value = false;
});

onMounted(async () => {
    try {
        const response = await listArchiveCategories(true);
        if (!disposed) categories.value = response.items;
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "加载档案分类失败");
    }
});
watch(
    () => form.categoryId,
    async (categoryId, previous) => {
        const loadVersion = ++categoryLoadVersion;
        if (typeof categoryId !== "number") {
            fields.value = [];
            relatedCategories.value = [];
            relatedFieldsByCategory.value = new Map();
            return;
        }
        if (previous !== undefined && previous !== categoryId) {
            form.conditions = [];
            form.fondsCode = undefined;
            form.keyword = undefined;
            fields.value = [];
            relatedCategories.value = [];
            relatedFieldsByCategory.value = new Map();
        }
        try {
            const [fieldResponse, relatedResponse] = await Promise.all([
                listArchiveFields(categoryId, "ITEM"),
                listArchiveRelatedFilterCategories(categoryId),
            ]);
            const ids = [
                ...new Set(
                    relatedResponse.items
                        .map((item) => item.categoryId)
                        .filter((id): id is number => typeof id === "number"),
                ),
            ];
            const responses = await Promise.all(ids.map((id) => listArchiveFields(id, "ITEM")));
            if (disposed || loadVersion !== categoryLoadVersion || form.categoryId !== categoryId)
                return;
            fields.value = fieldResponse.items;
            relatedCategories.value = relatedResponse.items;
            relatedFieldsByCategory.value = new Map(
                ids.map((id, index) => [id, responses[index]!.items]),
            );
        } catch (error) {
            if (disposed || loadVersion !== categoryLoadVersion) return;
            fields.value = [];
            relatedCategories.value = [];
            relatedFieldsByCategory.value = new Map();
            ElMessage.error(error instanceof Error ? error.message : "加载查询字段失败");
        }
    },
);
function execute(query: SearchArchiveRecordsQuery, nextCursor?: string) {
    const request: DiscoverRequest = {
        ...query,
        orderBy: orderBy.value.length ? orderBy.value.map((item) => ({ ...item })) : undefined,
        limit: limit.value,
        cursor: nextCursor,
    };
    return executeRequest(request);
}
async function executeRequest(request: DiscoverRequest, preserveError = false) {
    const version = ++requestVersion;
    loading.value = true;
    if (!preserveError) {
        loadError.value = undefined;
        failedRequest = undefined;
        retryInFlight = undefined;
    }
    cursor.value = request.cursor;
    try {
        const response = await discoverArchiveRecords(request);
        if (!disposed && version === requestVersion) {
            result.value = response;
            loadError.value = undefined;
            failedRequest = undefined;
        }
    } catch (error) {
        if (!disposed && version === requestVersion) {
            const cursorInvalid = Boolean(request.cursor) && isCursorFieldViolation(error);
            failedRequest = cursorInvalid ? { ...request, cursor: undefined } : request;
            if (cursorInvalid) {
                clearResultCursors();
                loadError.value = withRequestTraceId("数据已变化，将从第一页重新加载", error);
            } else loadError.value = requestErrorMessage(error, "查询失败");
        }
    } finally {
        if (!disposed && version === requestVersion) loading.value = false;
    }
}
function submit(values: ArchiveQueryFormValues) {
    const query = toSearchQuery(values);
    committedQuery.value = query;
    orderBy.value = [];
    clearResultCursors();
    void execute(query);
}
function orderResults(next: ArchiveRecordOrderBy[]) {
    if (!committedQuery.value) return;
    orderBy.value = next;
    clearResultCursors();
    void execute(committedQuery.value);
}
function refresh(): Promise<void> {
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
    if (committedQuery.value) return execute(committedQuery.value, cursor.value);
    return Promise.resolve();
}
function page(nextCursor: string) {
    if (committedQuery.value) void execute(committedQuery.value, nextCursor);
}
function limitChange(nextLimit: number) {
    limit.value = nextLimit;
    clearResultCursors();
    if (committedQuery.value) void execute(committedQuery.value);
}
function reset() {
    requestVersion += 1;
    loading.value = false;
    Object.assign(form, {
        categoryId: undefined,
        fondsCode: undefined,
        keyword: undefined,
        conditions: [],
        relatedGroups: [],
    });
    result.value = undefined;
    committedQuery.value = undefined;
    orderBy.value = [];
    cursor.value = undefined;
    loadError.value = undefined;
    failedRequest = undefined;
    retryInFlight = undefined;
}
function clearResultCursors() {
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
</script>

<template>
    <section class="am-page">
        <div class="am-page__header"><h1>档案搜索</h1></div>
        <el-collapse class="am-page__filter am-query-collapse" :model-value="['query']"
            ><el-collapse-item name="query" title="高级筛选条件"
                ><ArchiveAdvancedQueryPanel
                    :model-value="form"
                    @update:model-value="Object.assign(form, $event)"
                    :categories="categories"
                    :fields="fields"
                    :related-categories="relatedCategories"
                    :related-fields-by-category="relatedFieldsByCategory"
                    show-keyword
                    :submitting="loading"
                    @reset="reset"
                    @submit="submit" /></el-collapse-item
        ></el-collapse>
        <el-card class="am-page__result" shadow="never"
            ><RequestErrorState
                v-if="loadError"
                :message="loadError"
                :retrying="loading"
                @retry="refresh" /><ArchiveResultTable
                v-if="result"
                :result="result"
                :loading="loading"
                :order-by="orderBy"
                @order-change="orderResults" /><el-empty
                v-else-if="!loadError"
                description="选择分类并提交高级查询后显示结果" />
            <div v-if="result" class="am-table-footer">
                <CursorPagination
                    :limit="limit"
                    :prev="result.prev"
                    :next="result.next"
                    :loading="loading"
                    @page="page"
                    @limit-change="limitChange"
                /></div
        ></el-card>
    </section>
</template>
