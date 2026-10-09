<script setup lang="ts">
import { Plus } from "@element-plus/icons-vue";
import type { FormInstance } from "element-plus";
import { ref, watch } from "vue";

import type { ArchiveCategoryDto, ArchiveFieldDto } from "@/shared/types/archive-metadata";
import type {
    ArchiveItemRelationDirection,
    ArchiveRelatedFilterCategoryDto,
} from "@/shared/types/archive-records";
import ArchiveQueryConditionRow from "./ArchiveQueryConditionRow.vue";
import type { ArchiveQueryFormValues, QueryConditionDraft } from "./archiveQueryTypes";

export type {
    ArchiveQueryFormValues,
    QueryConditionDraft,
    RelatedGroupDraft,
} from "./archiveQueryTypes";

const props = withDefaults(
    defineProps<{
        categories: ArchiveCategoryDto[];
        fields: ArchiveFieldDto[];
        relatedCategories: ArchiveRelatedFilterCategoryDto[];
        relatedFieldsByCategory: Map<number, ArchiveFieldDto[]>;
        showKeyword: boolean;
        submitting?: boolean;
    }>(),
    { submitting: false },
);
const values = defineModel<ArchiveQueryFormValues>({
    default: () => ({ conditions: [], relatedGroups: [] }),
});
const emit = defineEmits<{ submit: [values: ArchiveQueryFormValues]; reset: [] }>();
const formRef = ref<FormInstance>();

watch(
    () => props.relatedCategories,
    (categories) => {
        values.value.relatedGroups = categories.map((category) => {
            const current = values.value.relatedGroups?.find(
                (group) =>
                    group.categoryId === category.categoryId &&
                    group.direction === category.direction,
            );
            return {
                categoryId: category.categoryId,
                direction: category.direction,
                conditions: current?.conditions ?? [],
            };
        });
    },
    { immediate: true },
);

function searchableFields(fields: ArchiveFieldDto[]) {
    return fields.filter(
        (field) => field.enabled && field.archiveLevel === "ITEM" && field.exactSearchable,
    );
}
function addCondition(conditions: QueryConditionDraft[] | undefined) {
    (conditions ??= []).push({ op: "EQ" });
    return conditions;
}
function relationDirectionLabel(direction: ArchiveItemRelationDirection) {
    return direction === "BOTH" ? "双向关联" : "当前关联出去";
}
async function submit() {
    if (!(await formRef.value?.validate().catch(() => false))) return;
    emit("submit", values.value);
}
</script>

<template>
    <el-form ref="formRef" :model="values" label-position="top" @submit.prevent="submit">
        <el-row :gutter="12">
            <el-col :span="6"
                ><el-form-item
                    label="档案分类"
                    prop="categoryId"
                    :rules="[{ required: true, message: '请选择档案分类', trigger: 'change' }]"
                    ><el-select v-model="values.categoryId" filterable placeholder="选择分类"
                        ><el-option
                            v-for="category in categories"
                            :key="category.id"
                            :label="category.categoryName"
                            :value="category.id" /></el-select></el-form-item
            ></el-col>
            <el-col :span="5"
                ><el-form-item label="全宗号"
                    ><el-input
                        v-model="values.fondsCode"
                        clearable
                        placeholder="按全宗号过滤" /></el-form-item
            ></el-col>
            <el-col v-if="showKeyword" :span="7"
                ><el-form-item label="全文关键词"
                    ><el-input
                        v-model="values.keyword"
                        clearable
                        placeholder="在全文投影中检索" /></el-form-item
            ></el-col>
            <el-col :span="6"
                ><el-form-item label=" "
                    ><el-button native-type="submit" :loading="submitting" type="primary"
                        >查询</el-button
                    ><el-button @click="emit('reset')">重置</el-button></el-form-item
                ></el-col
            >
        </el-row>
        <div class="am-query-section">
            <div class="am-query-section__toolbar">
                <span>本分类条件</span
                ><el-button
                    link
                    type="primary"
                    size="small"
                    :icon="Plus"
                    @click="values.conditions = addCondition(values.conditions)"
                    >添加条件</el-button
                >
            </div>
            <ArchiveQueryConditionRow
                v-for="(condition, index) in values.conditions"
                :key="index"
                :model-value="condition"
                :fields="searchableFields(fields)"
                :field-path="`conditions.${index}.fieldCode`"
                delete-label="删除条件"
                @update:model-value="values.conditions![index] = $event"
                @remove="values.conditions?.splice(index, 1)"
            />
        </div>
        <div class="am-query-section">
            <div class="am-query-section__toolbar"><span>关联分类条件</span></div>
            <el-empty
                v-if="relatedCategories.length === 0"
                description="当前分类暂无可用关联筛选分类"
                :image-size="48"
            />
            <div
                v-for="(group, index) in values.relatedGroups"
                :key="`${group.categoryId}:${group.direction}`"
                class="related-group"
            >
                <div class="am-query-section__toolbar">
                    <span>{{ relatedCategories[index]?.categoryName }}</span
                    ><el-text type="info">{{ relationDirectionLabel(group.direction!) }}</el-text>
                </div>
                <ArchiveQueryConditionRow
                    v-for="(condition, conditionIndex) in group.conditions"
                    :key="conditionIndex"
                    :model-value="condition"
                    :fields="searchableFields(relatedFieldsByCategory.get(group.categoryId!) ?? [])"
                    :field-path="`relatedGroups.${index}.conditions.${conditionIndex}.fieldCode`"
                    delete-label="删除关联条件"
                    @update:model-value="group.conditions![conditionIndex] = $event"
                    @remove="group.conditions?.splice(conditionIndex, 1)"
                />
                <el-button :icon="Plus" @click="group.conditions = addCondition(group.conditions)"
                    >添加关联字段条件</el-button
                >
            </div>
        </div>
    </el-form>
</template>

<style scoped>
.am-query-section {
    margin-top: 12px;
}
.am-query-section__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
}
.related-group {
    padding: 12px 0;
    border-top: 1px solid var(--el-border-color-lighter);
}
</style>
