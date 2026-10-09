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
import type { ArchiveRelatedFilterCategoryDto } from "@/shared/types/archive-records";
import type { ArchiveQueryFormValues } from "@/shared/archive/query/archiveQueryTypes";
import { toSearchQuery } from "@/shared/archive/query/archiveQuery";
import { useArchiveQueryResult } from "@/shared/archive/query/useArchiveQueryResult";

export function useArchiveItemSearch() {
    const queryForm = reactive<ArchiveQueryFormValues>({ conditions: [], relatedGroups: [] });
    const categories = ref<ArchiveCategoryDto[]>([]);
    const fonds = ref<Array<{ fondsCode: string; fondsName: string }>>([]);
    const fields = ref<ArchiveFieldDto[]>([]);
    const relatedCategories = ref<ArchiveRelatedFilterCategoryDto[]>([]);
    const relatedFieldsByCategory = ref(new Map<number, ArchiveFieldDto[]>());
    const {
        committedQuery,
        result,
        orderBy,
        limit,
        loading,
        loadError,
        refresh,
        page,
        limitChange,
        orderResults,
        submit: submitQuery,
        reset: resetResults,
    } = useArchiveQueryResult(searchArchiveRecords);
    let categoryLoadVersion = 0;
    let disposed = false;

    onBeforeUnmount(() => {
        disposed = true;
        categoryLoadVersion += 1;
    });

    onMounted(async () => {
        try {
            const [categoryResponse, fondsResponse] = await Promise.all([
                listArchiveCategories(true),
                listArchiveFonds("ACTIVE"),
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

    function submit(values: ArchiveQueryFormValues) {
        submitQuery({ ...toSearchQuery(values), keyword: undefined });
    }
    function reset() {
        resetResults();
        Object.assign(queryForm, {
            categoryId: undefined,
            fondsCode: undefined,
            keyword: undefined,
            conditions: [],
            relatedGroups: [],
        });
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
