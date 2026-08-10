<script setup lang="ts">
import { onMounted, ref } from "vue";

import { listArchiveFonds, updateArchiveFonds } from "@/shared/api/archive-metadata";
import { AmDataTable } from "@/shared/components/data-table";
import type { ArchiveFondsDto } from "@/shared/types/archive-metadata";

const fonds = ref<ArchiveFondsDto[]>([]);
const loading = ref(false);
const updatingId = ref<number>();

async function loadFonds() {
    loading.value = true;
    try {
        fonds.value = (await listArchiveFonds()).items;
    } finally {
        loading.value = false;
    }
}

async function toggleFonds(value: unknown, enabled: string | number | boolean) {
    const row = value as ArchiveFondsDto;
    updatingId.value = row.id;
    try {
        const updated = await updateArchiveFonds(row.id, {
            enabled: enabled === true,
            fondsCode: row.fondsCode,
            fondsName: row.fondsName,
            sortOrder: row.sortOrder,
        });
        const index = fonds.value.findIndex((item) => item.id === updated.id);
        if (index >= 0) fonds.value[index] = updated;
    } finally {
        updatingId.value = undefined;
    }
}

onMounted(loadFonds);
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>全宗管理</h1>
            <el-button type="primary">新建全宗</el-button>
        </div>
        <el-card shadow="never">
            <AmDataTable
                :data="fonds"
                :loading="loading"
                row-key="id"
                :columns="[
                    { key: 'fondsCode', label: '全宗号', width: 140, sortable: true },
                    { key: 'fondsName', label: '全宗名称', sortable: true },
                    { key: 'sortOrder', label: '排序', width: 100, sortable: true },
                    { key: 'enabled', label: '启用', width: 120, sortable: true },
                ]"
            >
                <template #cell-enabled="{ row }">
                    <el-switch
                        :model-value="row.enabled"
                        :loading="updatingId === row.id"
                        active-text="启用"
                        inactive-text="停用"
                        inline-prompt
                        :aria-label="`${row.enabled ? '停用' : '启用'}全宗：${row.fondsName}`"
                        @change="(enabled) => toggleFonds(row, enabled)"
                    />
                </template>
            </AmDataTable>
        </el-card>
    </section>
</template>
