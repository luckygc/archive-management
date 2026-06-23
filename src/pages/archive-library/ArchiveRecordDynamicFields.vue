<script setup lang="ts">
import { computed } from "vue";
import type { ArchiveFieldDto } from "../../shared/types/archive";

const props = defineProps<{
  fields: ArchiveFieldDto[];
  modelValue: Record<string, unknown>;
  mode: "detail" | "edit";
}>();

const emit = defineEmits<{
  "update:field": [fieldCode: string, value: unknown];
}>();

function fieldValue(field: ArchiveFieldDto) {
  return props.modelValue[field.fieldCode];
}

function colSpan(field: ArchiveFieldDto) {
  return props.mode === "detail" ? field.detailColSpan : field.editColSpan;
}

const layoutFields = computed(() =>
  [...props.fields]
    .filter((field) => (props.mode === "detail" ? field.detailVisible : field.editVisible))
    .sort((current, next) => {
      const currentOrder =
        props.mode === "detail" ? current.detailSortOrder : current.editSortOrder;
      const nextOrder = props.mode === "detail" ? next.detailSortOrder : next.editSortOrder;
      return currentOrder - nextOrder || current.id.localeCompare(next.id);
    }),
);

function fieldStyle(field: ArchiveFieldDto) {
  return {
    gridColumn: `span ${Math.min(Math.max(colSpan(field) || 1, 1), 2)}`,
  };
}

function inputType(field: ArchiveFieldDto) {
  return field.editControl === "datetime" ? "datetime" : "date";
}

function valueFormat(field: ArchiveFieldDto) {
  return field.editControl === "date" ? "YYYY-MM-DD" : "YYYY-MM-DDTHH:mm:ss";
}

function updateField(field: ArchiveFieldDto, value: unknown) {
  emit("update:field", field.fieldCode, value);
}
</script>

<template>
  <section class="archive-record-fields">
    <template v-for="field in layoutFields" :key="field.id">
      <div v-if="mode === 'detail'" class="archive-record-fields__item" :style="fieldStyle(field)">
        <span class="archive-record-fields__label">{{ field.fieldName }}</span>
        <span class="archive-record-fields__value">{{ fieldValue(field) ?? "-" }}</span>
      </div>
      <el-form-item v-else-if="mode === 'edit'" :label="field.fieldName" :style="fieldStyle(field)">
        <el-input-number
          v-if="field.editControl === 'number'"
          :model-value="fieldValue(field) as number"
          @update:model-value="(value: number | undefined) => updateField(field, value)"
        />
        <el-date-picker
          v-else-if="field.editControl === 'date' || field.editControl === 'datetime'"
          :model-value="fieldValue(field) as string"
          :type="inputType(field)"
          :value-format="valueFormat(field)"
          @update:model-value="(value: string | undefined) => updateField(field, value)"
        />
        <el-input
          v-else-if="field.editControl === 'textarea'"
          :model-value="fieldValue(field) as string"
          type="textarea"
          :rows="4"
          @update:model-value="(value: string) => updateField(field, value)"
        />
        <el-input
          v-else
          :model-value="fieldValue(field) as string"
          @update:model-value="(value: string) => updateField(field, value)"
        />
      </el-form-item>
    </template>
  </section>
</template>

<style scoped lang="scss">
.archive-record-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
}

.archive-record-fields__item {
  min-width: 0;
}

.archive-record-fields__label {
  display: block;
  margin-bottom: 6px;
  color: var(--am-text-muted);
  font-size: 13px;
}

.archive-record-fields__value {
  display: block;
  min-height: 32px;
  overflow-wrap: anywhere;
  color: var(--am-text);
  line-height: 1.55;
}

:deep(.el-form-item) {
  min-width: 0;
  margin-bottom: 0;
}

:deep(.el-date-editor.el-input),
:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
}
</style>
