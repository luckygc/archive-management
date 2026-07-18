<script lang="ts">
import { z } from "zod";

const archiveRecordFormValuesSchema = z.object({
    dynamicFields: z.record(z.string(), z.unknown()).default({}),
});

export function normalizeArchiveRecordFormValues(values: unknown) {
    return archiveRecordFormValuesSchema.parse(values);
}
</script>

<script setup lang="ts">
import { computed } from "vue";

import type { ArchiveFieldDto } from "@/shared/types/archive-metadata";

const props = withDefaults(
    defineProps<{
        fields: ArchiveFieldDto[];
        disabled?: boolean;
        fieldErrors?: Record<string, string>;
        surface?: "edit" | "detail";
    }>(),
    {
        disabled: false,
        fieldErrors: () => ({}),
        surface: "edit",
    },
);
const values = defineModel<Record<string, unknown>>({ default: () => ({}) });

const visibleFields = computed(() =>
    [...props.fields]
        .filter(
            (field) =>
                field.enabled &&
                (props.surface === "detail" ? field.detailVisible : field.editVisible),
        )
        .sort(
            (left, right) =>
                (props.surface === "detail"
                    ? left.detailSortOrder - right.detailSortOrder
                    : left.editSortOrder - right.editSortOrder) || left.sortOrder - right.sortOrder,
        ),
);

function update(fieldCode: string, value: unknown) {
    values.value = { ...values.value, [fieldCode]: value };
}
function fieldWidth(field: ArchiveFieldDto) {
    const colSpan = props.surface === "detail" ? field.detailColSpan : field.editColSpan;
    return Math.min(24, Math.max(8, (colSpan || 1) * 8));
}
function numberLimit(field: ArchiveFieldDto) {
    if (field.fieldType !== "INTEGER" && field.fieldType !== "DECIMAL") return undefined;
    const precision = field.decimalPrecision ?? 18;
    const scale = field.fieldType === "DECIMAL" ? (field.decimalScale ?? 2) : 0;
    return Number("9".repeat(Math.max(1, precision - scale)));
}
</script>

<template>
    <el-row :gutter="16">
        <el-col v-for="field in visibleFields" :key="field.id" :span="fieldWidth(field)">
            <el-form-item :label="field.fieldName" :error="fieldErrors[field.fieldCode]">
                <el-input
                    v-if="field.editControl === 'TEXTAREA'"
                    :model-value="String(values[field.fieldCode] ?? '')"
                    :disabled="disabled"
                    :maxlength="field.textLength"
                    type="textarea"
                    :rows="3"
                    @update:model-value="update(field.fieldCode, $event)"
                />
                <el-input-number
                    v-else-if="field.editControl === 'NUMBER'"
                    :model-value="
                        typeof values[field.fieldCode] === 'number'
                            ? (values[field.fieldCode] as number)
                            : undefined
                    "
                    :disabled="disabled"
                    :max="numberLimit(field)"
                    :precision="field.fieldType === 'DECIMAL' ? field.decimalScale : 0"
                    style="width: 100%"
                    @update:model-value="update(field.fieldCode, $event)"
                />
                <el-date-picker
                    v-else-if="field.editControl === 'DATE' || field.editControl === 'DATETIME'"
                    :model-value="values[field.fieldCode] as string | undefined"
                    :disabled="disabled"
                    :type="field.editControl === 'DATETIME' ? 'datetime' : 'date'"
                    :value-format="
                        field.editControl === 'DATETIME' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'
                    "
                    style="width: 100%"
                    @update:model-value="update(field.fieldCode, $event)"
                />
                <el-input
                    v-else
                    :model-value="String(values[field.fieldCode] ?? '')"
                    :disabled="disabled"
                    :maxlength="field.textLength"
                    @update:model-value="update(field.fieldCode, $event)"
                />
            </el-form-item>
        </el-col>
    </el-row>
</template>
