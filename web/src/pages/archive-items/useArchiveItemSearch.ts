import { ElMessage } from "element-plus";
import { onMounted, reactive, ref, watch } from "vue";

import { errorMessage } from "@archive-management/frontend-core/api";

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

    onMounted(async () => {
        try {
            const [categoryResponse, fondsResponse] = await Promise.all([
                listArchiveCategories(true),
                listArchiveFonds(true),
            ]);
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
                if (loadVersion !== categoryLoadVersion || queryForm.categoryId !== categoryId)
                    return;
                fields.value = fieldResponse.items;
                relatedCategories.value = relatedResponse.items;
                relatedFieldsByCategory.value = new Map(
                    ids.map((id, index) => [id, responses[index]!.items]),
                );
            } catch (error) {
                if (loadVersion !== categoryLoadVersion) return;
                fields.value = [];
                relatedCategories.value = [];
                relatedFieldsByCategory.value = new Map();
                ElMessage.error(error instanceof Error ? error.message : "加载查询字段失败");
            }
        },
    );

    async function execute(query: SearchArchiveRecordsQuery, nextCursor?: string) {
        const version = ++requestVersion;
        const request = {
            ...query,
            orderBy: orderBy.value.length ? orderBy.value.map((item) => ({ ...item })) : undefined,
            limit: limit.value,
            cursor: nextCursor,
        };
        loading.value = true;
        loadError.value = undefined;
        cursor.value = nextCursor;
        try {
            const response = await searchArchiveRecords(request);
            if (version === requestVersion) result.value = response;
        } catch (error) {
            if (version === requestVersion) loadError.value = errorMessage(error, "查询失败");
        } finally {
            if (version === requestVersion) loading.value = false;
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
