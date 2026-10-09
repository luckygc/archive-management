import { onBeforeUnmount, ref } from "vue";

import {
    isCursorFieldViolation,
    requestErrorMessage,
    withRequestTraceId,
} from "@/shared/requestError";
import type {
    ArchiveRecordListDto,
    ArchiveRecordOrderBy,
    SearchArchiveRecordsQuery,
} from "@/shared/types/archive-records";

export function useArchiveQueryResult(
    queryRecords: (query: SearchArchiveRecordsQuery) => Promise<ArchiveRecordListDto>,
) {
    const result = ref<ArchiveRecordListDto>();
    const committedQuery = ref<SearchArchiveRecordsQuery>();
    const orderBy = ref<ArchiveRecordOrderBy[]>([]);
    const limit = ref(100);
    const cursor = ref<string>();
    const loading = ref(false);
    const loadError = ref<string>();
    let requestVersion = 0;
    let disposed = false;
    let failedRequest: SearchArchiveRecordsQuery | undefined;
    let retryInFlight: Promise<void> | undefined;

    onBeforeUnmount(() => {
        disposed = true;
        requestVersion += 1;
        loading.value = false;
    });

    function execute(query: SearchArchiveRecordsQuery, nextCursor?: string) {
        return executeRequest({
            ...query,
            orderBy: orderBy.value.length ? orderBy.value.map((item) => ({ ...item })) : undefined,
            limit: limit.value,
            cursor: nextCursor,
        });
    }

    async function executeRequest(request: SearchArchiveRecordsQuery, preserveError = false) {
        const version = ++requestVersion;
        loading.value = true;
        if (!preserveError) {
            loadError.value = undefined;
            failedRequest = undefined;
            retryInFlight = undefined;
        }
        cursor.value = request.cursor;
        try {
            const response = await queryRecords(request);
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

    function submit(query: SearchArchiveRecordsQuery) {
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
        committedQuery,
        limit,
        limitChange,
        loading,
        loadError,
        orderBy,
        orderResults,
        page,
        refresh,
        reset,
        result,
        submit,
    };
}
