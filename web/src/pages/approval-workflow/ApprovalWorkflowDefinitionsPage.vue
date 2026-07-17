<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";

import {
    listApprovalWorkflowDefinitions,
    publishApprovalWorkflowDefinition,
    setApprovalWorkflowDefinitionEnabled,
} from "@/shared/api/approval-workflow";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import type { ApprovalWorkflowDefinitionDto } from "@/shared/types/approval-workflow";

const router = useRouter();
const definitions = ref<ApprovalWorkflowDefinitionDto[]>([]);
const loading = ref(false);
const limit = ref(100);
const prev = ref<string>();
const next = ref<string>();

async function loadDefinitions(cursor?: string) {
    loading.value = true;
    try {
        const response = await listApprovalWorkflowDefinitions({ limit: limit.value, cursor });
        definitions.value = response.items;
        prev.value = response.prev;
        next.value = response.next;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        loading.value = false;
    }
}

function openDesigner(id: number | "new") {
    void router.push({ name: "approval-workflow-designer", params: { id } });
}

async function publish(value: unknown) {
    const row = value as ApprovalWorkflowDefinitionDto;
    try {
        await ElMessageBox.confirm(
            "发布后，新发起的实例使用新版本；运行中的实例继续使用原版本。确认发布？",
            "发布审批流",
            { type: "warning" },
        );
        const version = await publishApprovalWorkflowDefinition(row.id);
        ElMessage.success(`审批流已发布为版本 ${version.versionNumber}`);
        await loadDefinitions();
    } catch (error) {
        if (error !== "cancel" && error !== "close") ElMessage.error((error as Error).message);
    }
}

async function toggleEnabled(value: unknown) {
    const row = value as ApprovalWorkflowDefinitionDto;
    try {
        await setApprovalWorkflowDefinitionEnabled(row.id, !row.enabled);
        ElMessage.success(row.enabled ? "审批流已停用" : "审批流已启用");
        await loadDefinitions();
    } catch (error) {
        ElMessage.error((error as Error).message);
    }
}

onMounted(() => void loadDefinitions());
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <div>
                <h1>审批流程</h1>
                <p class="page-description">在可视化画布中设计审批节点、条件分支和办理人。</p>
            </div>
            <el-button type="primary" @click="openDesigner('new')">新建流程</el-button>
        </div>
        <el-card shadow="never">
            <el-table v-loading="loading" :data="definitions" row-key="id">
                <el-table-column prop="definitionName" label="流程名称" min-width="190" />
                <el-table-column prop="definitionCode" label="定义编码" width="190" />
                <el-table-column prop="businessType" label="业务类型" width="160" />
                <el-table-column label="画布节点" width="100">
                    <template #default="{ row }">{{ row.graph.nodes.length }}</template>
                </el-table-column>
                <el-table-column prop="draftRevision" label="草稿修订" width="100" />
                <el-table-column label="发布状态" width="110">
                    <template #default="{ row }">
                        <el-tag :type="row.publishedVersionId ? 'primary' : 'info'">
                            {{ row.publishedVersionId ? "已发布" : "未发布" }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="启用状态" width="100">
                    <template #default="{ row }">
                        <el-tag :type="row.enabled ? 'success' : 'info'">
                            {{ row.enabled ? "启用" : "停用" }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="230" fixed="right">
                    <template #default="{ row }">
                        <el-button link type="primary" @click="openDesigner(row.id)">设计</el-button>
                        <el-button link @click="publish(row)">发布</el-button>
                        <el-button link @click="toggleEnabled(row)">
                            {{ row.enabled ? "停用" : "启用" }}
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>
            <el-empty v-if="!loading && definitions.length === 0" description="暂无审批流程">
                <el-button type="primary" @click="openDesigner('new')">创建第一个流程</el-button>
            </el-empty>
            <CursorPagination
                :limit="limit"
                :prev="prev"
                :next="next"
                :loading="loading"
                @page="loadDefinitions"
                @limit-change="
                    (value) => {
                        limit = value;
                        loadDefinitions();
                    }
                "
            />
        </el-card>
    </section>
</template>

<style scoped>
.page-description {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
}
</style>
