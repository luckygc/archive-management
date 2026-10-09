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
import type { ArchiveRelatedFilterCategoryDto } from "@/shared/types/archive-records";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import { useArchiveQueryResult } from "@/shared/archive/query/useArchiveQueryResult";

import ArchiveAdvancedQueryPanel from "@/shared/archive/query/ArchiveAdvancedQueryPanel.vue";
import type { ArchiveQueryFormValues } from "@/shared/archive/query/archiveQueryTypes";
import ArchiveResultTable from "@/shared/archive/result-table/ArchiveResultTable.vue";
import { toSearchQuery } from "@/shared/archive/query/archiveQuery";

const form = reactive<ArchiveQueryFormValues>({ conditions: [], relatedGroups: [] });
const categories = ref<ArchiveCategoryDto[]>([]);
const fields = ref<ArchiveFieldDto[]>([]);
const relatedCategories = ref<ArchiveRelatedFilterCategoryDto[]>([]);
const relatedFieldsByCategory = ref(new Map<number, ArchiveFieldDto[]>());
const {
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
} = useArchiveQueryResult(discoverArchiveRecords);
let categoryLoadVersion = 0;
let disposed = false;

onBeforeUnmount(() => {
    disposed = true;
    categoryLoadVersion += 1;
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
function submit(values: ArchiveQueryFormValues) {
    submitQuery(toSearchQuery(values));
}
function reset() {
    resetResults();
    Object.assign(form, {
        categoryId: undefined,
        fondsCode: undefined,
        keyword: undefined,
        conditions: [],
        relatedGroups: [],
    });
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
                    :total="result.total"
                    :prev="result.prev"
                    :next="result.next"
                    :loading="loading"
                    @page="page"
                    @limit-change="limitChange"
                /></div
        ></el-card>
    </section>
</template>
