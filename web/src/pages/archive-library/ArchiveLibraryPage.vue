<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref, watch } from "vue";

import {
    discoverArchiveRecords,
    listArchiveCategories,
    listArchiveFields,
    listArchiveRelatedFilterCategories,
} from "@/shared/api/archive";
import type {
    ArchiveCategoryDto,
    ArchiveFieldDto,
    ArchiveRecordListDto,
    ArchiveRecordOrderBy,
    ArchiveRelatedFilterCategoryDto,
    SearchArchiveRecordsQuery,
} from "@/shared/types/archive";

import ArchiveAdvancedQueryPanel from "./ArchiveAdvancedQueryPanel.vue";
import type { ArchiveQueryFormValues } from "./archiveQueryTypes";
import ArchiveResultTable from "./ArchiveResultTable.vue";
import { toSearchQuery } from "./archiveQuery";

const form = reactive<ArchiveQueryFormValues>({ conditions: [], relatedGroups: [] });
const categories = ref<ArchiveCategoryDto[]>([]);
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
        categories.value = (await listArchiveCategories(true)).items;
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
            result.value = undefined;
            orderBy.value = [];
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
            if (loadVersion !== categoryLoadVersion || form.categoryId !== categoryId) return;
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
        result.value = await discoverArchiveRecords(query);
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "查询失败");
    } finally {
        loading.value = false;
    }
}
function submit(values: ArchiveQueryFormValues) {
    const query = toSearchQuery(values);
    committedQuery.value = query;
    orderBy.value = [];
    void execute(query);
}
function orderResults(next: ArchiveRecordOrderBy[]) {
    if (!committedQuery.value) return;
    orderBy.value = next;
    void execute({ ...committedQuery.value, orderBy: next.length ? next : undefined });
}
function reset() {
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
            ><ArchiveResultTable
                v-if="result"
                :result="result"
                :loading="loading"
                :order-by="orderBy"
                @order-change="orderResults" /><el-empty
                v-else
                description="选择分类并提交高级查询后显示结果"
        /></el-card>
    </section>
</template>
