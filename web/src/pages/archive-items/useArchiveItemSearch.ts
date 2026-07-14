import { ElMessage } from "element-plus";
import { onMounted, reactive, ref, watch } from "vue";

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
    const loading = ref(false);
    let categoryLoadVersion = 0;

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
                result.value = undefined;
                committedQuery.value = undefined;
                orderBy.value = [];
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

    async function execute(query: SearchArchiveRecordsQuery) {
        loading.value = true;
        try {
            result.value = await searchArchiveRecords(query);
        } catch (error) {
            ElMessage.error(error instanceof Error ? error.message : "查询失败");
        } finally {
            loading.value = false;
        }
    }
    function submit(values: ArchiveQueryFormValues) {
        const query = { ...toSearchQuery(values), keyword: undefined };
        committedQuery.value = query;
        orderBy.value = [];
        void execute(query);
    }
    function refresh() {
        if (committedQuery.value)
            void execute({
                ...committedQuery.value,
                orderBy: orderBy.value.length ? orderBy.value : undefined,
            });
    }
    function reset() {
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
    }
    function orderResults(next: ArchiveRecordOrderBy[]) {
        if (!committedQuery.value) return;
        orderBy.value = next;
        void execute({ ...committedQuery.value, orderBy: next.length ? next : undefined });
    }

    return {
        categories,
        committedQuery,
        fields,
        fonds,
        loading,
        orderBy,
        orderResults,
        queryForm,
        refresh,
        relatedCategories,
        relatedFieldsByCategory,
        reset,
        result,
        submit,
    };
}
