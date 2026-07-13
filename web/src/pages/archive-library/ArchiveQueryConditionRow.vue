<script setup lang="ts">
import { computed } from "vue";
import { Delete } from "@element-plus/icons-vue";

import type { ArchiveFieldDto } from "@/shared/types/archive";
import ArchiveQueryValueInput from "./ArchiveQueryValueInput.vue";
import type { QueryConditionDraft } from "./archiveQueryTypes";

const props = defineProps<{
    fields: ArchiveFieldDto[];
    modelValue: QueryConditionDraft;
    fieldPath: string;
    deleteLabel: string;
}>();
const emit = defineEmits<{
    "update:modelValue": [value: QueryConditionDraft];
    remove: [];
}>();
const selectedField = computed(() =>
    props.fields.find((field) => field.fieldCode === props.modelValue.fieldCode),
);
const operatorOptions = [
    { label: "等于", value: "EQ" },
    { label: "包含", value: "CONTAINS" },
    { label: "开头是", value: "STARTS_WITH" },
    { label: "大于等于", value: "GTE" },
    { label: "小于等于", value: "LTE" },
    { label: "区间", value: "BETWEEN" },
    { label: "为空", value: "IS_EMPTY" },
    { label: "不为空", value: "IS_NOT_EMPTY" },
];

function update(key: keyof QueryConditionDraft, value: unknown) {
    emit("update:modelValue", { ...props.modelValue, [key]: value });
}
</script>

<template>
    <div class="condition-row">
        <el-form-item
            :prop="fieldPath"
            :rules="[{ required: true, message: '请选择查询字段', trigger: 'change' }]"
        >
            <el-select
                :model-value="modelValue.fieldCode"
                filterable
                placeholder="字段"
                @update:model-value="update('fieldCode', $event)"
            >
                <el-option
                    v-for="field in fields"
                    :key="field.fieldCode"
                    :label="field.fieldName"
                    :value="field.fieldCode"
                />
            </el-select>
        </el-form-item>
        <el-select :model-value="modelValue.op ?? 'EQ'" @update:model-value="update('op', $event)">
            <el-option
                v-for="option in operatorOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
            />
        </el-select>
        <template v-if="modelValue.op === 'BETWEEN'">
            <ArchiveQueryValueInput
                :model-value="modelValue.startValue"
                :field="selectedField"
                placeholder="开始值"
                @update:model-value="update('startValue', $event)"
            />
            <ArchiveQueryValueInput
                :model-value="modelValue.endValue"
                :field="selectedField"
                placeholder="结束值"
                @update:model-value="update('endValue', $event)"
            />
        </template>
        <el-input
            v-else-if="modelValue.op === 'IS_EMPTY' || modelValue.op === 'IS_NOT_EMPTY'"
            disabled
            placeholder="无需输入值"
        />
        <ArchiveQueryValueInput
            v-else
            :model-value="modelValue.value"
            :field="selectedField"
            placeholder="查询值"
            @update:model-value="update('value', $event)"
        />
        <el-button
            type="danger"
            plain
            :icon="Delete"
            :aria-label="deleteLabel"
            @click="emit('remove')"
        />
    </div>
</template>

<style scoped>
.condition-row {
    display: grid;
    grid-template-columns: minmax(160px, 1fr) 120px minmax(180px, 1fr) auto;
    gap: 8px;
    align-items: start;
    margin-top: 8px;
}
.condition-row:has(> :nth-child(5)) {
    grid-template-columns: minmax(160px, 1fr) 120px minmax(140px, 1fr) minmax(140px, 1fr) auto;
}
.condition-row :deep(.el-form-item) {
    margin-bottom: 0;
}
</style>
