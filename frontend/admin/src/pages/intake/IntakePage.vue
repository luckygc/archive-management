<script setup lang="ts">
import { onMounted, ref } from "vue";

import { getIntakeOverview } from "@/shared/api/intake";
import type { IntakeOverviewDto } from "@/shared/types/intake";

const data = ref<IntakeOverviewDto>();
const error = ref(false);
const loading = ref(true);

onMounted(async () => {
    try {
        data.value = await getIntakeOverview();
    } catch {
        error.value = true;
    } finally {
        loading.value = false;
    }
});
</script>

<template>
    <main v-loading="loading" class="am-page intake-page">
        <el-alert v-if="error" title="归档接收入口加载失败" type="error" show-icon />
        <template v-else-if="!loading">
            <el-card shadow="never">
                <el-descriptions title="归档接收" :column="1" size="small">
                    <el-descriptions-item label="外部连接">
                        {{ data?.externalConnectionConfigured ? "已配置" : "未配置" }}
                    </el-descriptions-item>
                    <el-descriptions-item label="当前状态">{{ data?.status }}</el-descriptions-item>
                    <el-descriptions-item label="说明">{{ data?.message }}</el-descriptions-item>
                </el-descriptions>
            </el-card>
            <el-empty description="数据源接入、清洗、字段映射和暂存处理入口已预留" />
        </template>
    </main>
</template>

<style scoped>
.intake-page {
    display: flex;
    flex-direction: column;
    gap: 16px;
    min-height: 160px;
}
</style>
