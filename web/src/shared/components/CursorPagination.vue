<script lang="ts">
export const CURSOR_PAGE_SIZE_OPTIONS = [
    { label: "100 条", value: 100 },
    { label: "200 条", value: 200 },
    { label: "500 条", value: 500 },
    { label: "1000 条", value: 1000 },
];
</script>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
    limit: number;
    total?: number;
    prev?: string | null;
    next?: string | null;
    loading?: boolean;
}>();

const pageCount = computed(() =>
    props.total === undefined ? undefined : Math.ceil(props.total / props.limit),
);

const emit = defineEmits<{
    limitChange: [limit: number];
    page: [cursor: string];
}>();
</script>

<template>
    <div class="cursor-pagination">
        <span v-if="props.total !== undefined" class="cursor-pagination__summary">
            共 {{ props.total }} 条 · 共 {{ pageCount }} 页
        </span>
        <el-select
            :model-value="props.limit"
            aria-label="每页条数"
            :disabled="props.loading"
            class="cursor-pagination__size"
            @change="(value: number) => emit('limitChange', value)"
        >
            <el-option
                v-for="option in CURSOR_PAGE_SIZE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
            />
        </el-select>
        <el-button
            :disabled="!props.prev || props.loading"
            @click="props.prev && emit('page', props.prev)"
            >上一页</el-button
        >
        <el-button
            :disabled="!props.next || props.loading"
            @click="props.next && emit('page', props.next)"
            >下一页</el-button
        >
    </div>
</template>

<style scoped>
.cursor-pagination {
    display: flex;
    align-items: center;
    gap: 8px;
}
.cursor-pagination__size {
    width: 112px;
}
</style>
