<script setup lang="ts">
import { computed } from "vue";

import type { ArchiveFieldDto } from "@/shared/types/archive-metadata";

const props = defineProps<{ field?: ArchiveFieldDto; modelValue?: unknown; placeholder: string }>();
const emit = defineEmits<{ "update:modelValue": [value: unknown] }>();
const inputType = computed(() => props.field?.fieldType);
</script>

<template>
    <el-input-number
        v-if="inputType === 'INTEGER'"
        :model-value="modelValue as number | undefined"
        :precision="0"
        style="width: 100%"
        :placeholder="placeholder"
        @update:model-value="emit('update:modelValue', $event)"
    />
    <el-input-number
        v-else-if="inputType === 'DECIMAL'"
        :model-value="modelValue as number | undefined"
        :precision="field?.decimalScale ?? 2"
        style="width: 100%"
        :placeholder="placeholder"
        @update:model-value="emit('update:modelValue', $event)"
    />
    <el-date-picker
        v-else-if="inputType === 'DATE' || inputType === 'DATETIME'"
        :model-value="modelValue as string | undefined"
        :type="inputType === 'DATETIME' ? 'datetime' : 'date'"
        :value-format="inputType === 'DATETIME' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'"
        style="width: 100%"
        :placeholder="placeholder"
        @update:model-value="emit('update:modelValue', $event)"
    />
    <el-input
        v-else
        :model-value="modelValue as string | undefined"
        :placeholder="placeholder"
        @update:model-value="emit('update:modelValue', $event)"
    />
</template>
