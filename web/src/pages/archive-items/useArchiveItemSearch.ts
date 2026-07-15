import { ElMessage } from "element-plus";
import { onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";

import { searchArchiveRecords } from "@/shared/api/archive-records";
import {
    listArchiveCategories,
    listArchiveFields,
    listArchiveFonds,
    listArchiveRelatedFilterCategories,
} from "@/shared/api/archive-metadata";
import type { ArchiveCategoryDto, ArchiveFieldDto } from "@/shared/types/archive-metadata";
import type {
    ArchiveRecordListDto,
    ArchiveRecordOrderBy,
    ArchiveRelatedFilterCategoryDto,
    SearchArchiveRecordsQuery,
} from "@/shared/types/archive-records";
import type { ArchiveQueryFormValues } from "@/pages/archive-library/archiveQueryTypes";
import { toSearchQuery } from "@/pages/archive-library/archiveQuery";
import {
    isCursorFieldViolation,
    requestErrorMessage,
    withRequestTraceId,
} from "@/shared/requestError";

type SearchRequest = Parameters<typeof searchArchiveRecords>[0];

export function useArchiveItemSearch() {
    const queryForm = reactive<ArchiveQueryFormValues>({ conditions: [], relatedGroups: [] });
    const categories = ref<ArchiveCategoryDto[]>([]);
    const fonds = ref<Array<{ fondsCode: string; fondsName: string }>>([]);
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
    let failedRequest: SearchRequest | undefined;
    let retryInFlight: Promise<void> | undefined;

    onBeforeUnmount(() => {
        disposed = true;
        requestVersion += 1;
        categoryLoadVersion += 1;
        loading.value = false;
    });

    onMounted(async () => {
        try {
            const [categoryResponse, fondsResponse] = await Promise.all([
                listArchiveCategories(true),
                listArchiveFonds(true),
            ]);
            if (disposed) return;
            categories.value = categoryResponse.items;
            fonds.value = fondsResponse.items;
        } catch (error) {
            ElMessage.error(error instanceof Error ? error.message : "加载档案管理基础数据失败");
        }
    });

    watch(
        () => queryForm.categoryId,
        async (categoryId, previous) => {
            const loadVersion = ++categoryLoadVersion;
            if (typeof categoryId !== "number") {
                fields.value = [];
                relatedCategories.value = [];
                relatedFieldsByCategory.value = new Map();
                return;
            }
            if (previous !== undefined && previous !== categoryId) {
                queryForm.conditions = [];
                queryForm.relatedGroups = [];
                fields.value = [];
                relatedCategories.value = [];
                relatedFieldsByCategory.value = new Map();
            }
            try {
                const [fieldResponse, relatedResponse] = await Promise.all([
                    listArchiveFields(categoryId, "ITEM"),
                    listArchiveRelatedFilterCategories(categoryId),
                ]);
                const ids = [...new Set(relatedResponse.items.map((item) => item.categoryId))];
                const responses = await Promise.all(ids.map((id) => listArchiveFields(id, "ITEM")));
                if (
                    disposed ||
                    loadVersion !== categoryLoadVersion ||
                    queryForm.categoryId !== categoryId
                )
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
        const request: SearchRequest = {
            ...query,
            orderBy: orderBy.value.length ? orderBy.value.map((item) => ({ ...item })) : undefined,
            limit: limit.value,
            cursor: nextCursor,
        };
        return executeRequest(request);
    }

    async function executeRequest(request: SearchRequest, preserveError = false) {
        const version = ++requestVersion;
        loading.value = true;
        if (!preserveError) {
            loadError.value = undefined;
            failedRequest = undefined;
            retryInFlight = undefined;
        }
        cursor.value = request.cursor;
        try {
            const response = await searchArchiveRecords(request);
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
                    cursor.value = undefined;
                    loadError.value = withRequestTraceId("数据已变化，将从第一页重新加载", error);
                } else loadError.value = requestErrorMessage(error, "查询失败");
            }
        } finally {
            if (!disposed && version === requestVersion) loading.value = false;
        }
    }
    function submit(values: ArchiveQueryFormValues) {
        const query = { ...toSearchQuery(values), keyword: undefined };
        committedQuery.value = query;
        orderBy.value = [];
        clearResultCursors();
        void execute(query);
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
        Object.assign(queryForm, {
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
    function orderResults(next: ArchiveRecordOrderBy[]) {
        if (!committedQuery.value) return;
        orderBy.value = next;
        clearResultCursors();
        void execute(committedQuery.value);
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

    return {
        categories,
        committedQuery,
        fields,
        fonds,
        limit,
        limitChange,
        loading,
        loadError,
        orderBy,
        orderResults,
        page,
        queryForm,
        refresh,
        relatedCategories,
        relatedFieldsByCategory,
        reset,
        result,
        submit,
    };
}
