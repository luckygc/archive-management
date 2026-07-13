<script setup lang="ts">
import { onMounted, ref } from "vue";

import { listArchiveFonds, updateArchiveFonds } from "@/shared/api/archive";
import type { ArchiveFondsDto } from "@/shared/types/archive";

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
            <el-table v-loading="loading" :data="fonds" row-key="id">
                <el-table-column label="全宗号" prop="fondsCode" width="140" />
                <el-table-column label="全宗名称" prop="fondsName" />
                <el-table-column label="排序" prop="sortOrder" width="100" />
                <el-table-column label="启用" width="120">
                    <template #default="{ row }">
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
                </el-table-column>
            </el-table>
        </el-card>
    </section>
</template>
