<script setup lang="ts">
import { ElSkeleton } from "element-plus";
import { onBeforeUnmount, onMounted, ref } from "vue";

import { errorMessage } from "@archive-management/frontend-core/api";

import { getWorkspaceSummary } from "@/shared/api/workspace";
import type { WorkspaceSummaryResponse } from "@/shared/types/workspace";

const summary = ref<WorkspaceSummaryResponse>();
const loading = ref(false);
const loadError = ref<string>();
let disposed = false;
let requestVersion = 0;

onMounted(() => void loadSummary());

onBeforeUnmount(() => {
    disposed = true;
    requestVersion += 1;
});

async function loadSummary() {
    if (loading.value || disposed) return;
    const version = ++requestVersion;
    loading.value = true;
    loadError.value = undefined;
    try {
        const response = await getWorkspaceSummary();
        if (!disposed && version === requestVersion) summary.value = response;
    } catch (error) {
        if (!disposed && version === requestVersion) {
            loadError.value = errorMessage(error, "加载档案摘要失败");
        }
    } finally {
        if (!disposed && version === requestVersion) loading.value = false;
    }
}

const metrics: Array<{ label: string; key: keyof WorkspaceSummaryResponse }> = [
    { label: "档案总数", key: "archiveItemCount" },
    { label: "草稿档案", key: "draftCount" },
    { label: "已锁定档案", key: "lockedCount" },
    { label: "电子文件", key: "electronicFileCount" },
];
</script>

<template>
    <section class="am-page">
        <div class="am-page__header"><h1>工作台</h1></div>
        <el-card class="dashboard-summary" header="档案概览" shadow="never">
            <div
                v-if="loading && !summary"
                class="dashboard-summary__loading"
                aria-label="正在加载档案摘要"
                aria-live="polite"
            >
                <ElSkeleton :rows="2" animated />
            </div>
            <el-alert
                v-else-if="loadError"
                title="档案摘要加载失败"
                type="error"
                show-icon
                :closable="false"
            >
                <p class="dashboard-summary__error">{{ loadError }}</p>
                <el-button
                    link
                    :loading="loading"
                    aria-label="重试加载档案摘要"
                    @click="loadSummary"
                >
                    重试
                </el-button>
            </el-alert>
            <dl v-else-if="summary" class="dashboard-summary__metrics" aria-live="polite">
                <div v-for="metric in metrics" :key="metric.key" class="dashboard-summary__metric">
                    <dt>{{ metric.label }}</dt>
                    <dd>{{ summary[metric.key].toLocaleString() }}</dd>
                </div>
            </dl>
        </el-card>
    </section>
</template>

<style scoped>
.dashboard-summary__loading {
    min-height: 96px;
}

.dashboard-summary__error {
    margin: 4px 0 8px;
}

.dashboard-summary__metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    margin: 0;
}

.dashboard-summary__metric {
    min-width: 0;
    padding: 8px 20px;
    border-right: 1px solid var(--el-border-color-lighter);
}

.dashboard-summary__metric:first-child {
    padding-left: 8px;
}

.dashboard-summary__metric:last-child {
    border-right: 0;
}

.dashboard-summary__metric dt {
    color: var(--el-text-color-secondary);
    font-size: 14px;
    line-height: 22px;
}

.dashboard-summary__metric dd {
    margin: 8px 0 0;
    color: var(--el-text-color-primary);
    font-size: 28px;
    font-weight: 600;
    line-height: 36px;
    font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
    .dashboard-summary__metrics {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        row-gap: 16px;
    }

    .dashboard-summary__metric:nth-child(2) {
        border-right: 0;
    }

    .dashboard-summary__metric:nth-child(3) {
        padding-left: 8px;
    }
}

@media (max-width: 560px) {
    .dashboard-summary__metrics {
        grid-template-columns: 1fr;
    }

    .dashboard-summary__metric {
        padding: 8px;
        border-right: 0;
        border-bottom: 1px solid var(--el-border-color-lighter);
    }

    .dashboard-summary__metric:last-child {
        border-bottom: 0;
    }
}
</style>
