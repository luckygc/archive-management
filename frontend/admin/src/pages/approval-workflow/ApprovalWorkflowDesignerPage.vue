<script setup lang="ts">
import { ArrowLeft, Check } from "@element-plus/icons-vue";
import { ref } from "vue";
import { AmDataTable } from "@/shared/components/data-table";
import ApprovalWorkflowCanvas from "./ApprovalWorkflowCanvas.vue";
import ApprovalWorkflowPropertiesPanel from "./ApprovalWorkflowPropertiesPanel.vue";
import { useApprovalWorkflowDefinition } from "./useApprovalWorkflowDefinition";

const designer = ref<InstanceType<typeof ApprovalWorkflowCanvas>>();
const selected = ref<{ kind: "node" | "edge"; id: string }>();
const {
    router,
    definitionId,
    loading,
    saving,
    publishing,
    dirty,
    graph,
    form,
    userOptions,
    versions,
    versionsOpen,
    versionsLoading,
    issues,
    markDirty,
    updateGraph,
    save,
    publish,
    openVersions,
} = useApprovalWorkflowDefinition();

function setDefaultFlow(id: string, value: boolean) {
    const selectedEdge = graph.value.edges.find((edge) => edge.edgeCode === id);
    if (!selectedEdge) return;
    if (value) {
        for (const edge of graph.value.edges.filter(
            (edge) => edge.sourceNodeCode === selectedEdge.sourceNodeCode && edge.edgeCode !== id,
        )) {
            designer.value?.updateEdge(edge.edgeCode, { defaultFlow: false });
        }
    }
    designer.value?.updateEdge(id, {
        defaultFlow: value,
        condition: value ? undefined : selectedEdge.condition,
    });
}
function focusIssue(id?: string) {
    if (id) designer.value?.focusElement(id);
}
</script>

<template>
    <section class="designer-page" v-loading="loading">
        <header class="designer-header">
            <div class="designer-header__identity">
                <el-button
                    text
                    :icon="ArrowLeft"
                    aria-label="返回流程定义"
                    @click="router.push({ name: 'approval-workflow-definitions' })"
                />
                <div>
                    <h1>{{ form.definitionName || "新建审批流程" }}</h1>
                    <p>
                        {{ definitionId ? `草稿 #${definitionId}` : "尚未保存"
                        }}<span v-if="dirty"> · 有未保存修改</span>
                    </p>
                </div>
            </div>
            <div class="designer-header__actions">
                <el-button @click="openVersions">版本记录</el-button>
                <el-button :loading="saving" @click="save()">保存草稿</el-button>
                <el-button type="primary" :icon="Check" :loading="publishing" @click="publish"
                    >发布流程</el-button
                >
            </div>
        </header>

        <div class="designer-workspace">
            <ApprovalWorkflowCanvas
                ref="designer"
                :graph="graph"
                :loading="loading"
                @change="updateGraph"
                @select="selected = $event"
            />
            <ApprovalWorkflowPropertiesPanel
                v-model="form"
                :graph="graph"
                :selected="selected"
                :definition-id="definitionId"
                :user-options="userOptions"
                :issues="issues"
                @update-node="(id, values) => designer?.updateNode(id, values)"
                @update-edge="(id, values) => designer?.updateEdge(id, values)"
                @default-flow="setDefaultFlow"
                @metadata-change="markDirty"
                @focus-issue="focusIssue"
            />
        </div>

        <el-drawer v-model="versionsOpen" title="发布版本" size="520px">
            <AmDataTable
                :data="versions"
                :loading="versionsLoading"
                row-key="id"
                :columns="[
                    { key: 'versionNumber', label: '版本', width: 90, sortable: true },
                    { key: 'publishedBy', label: '发布人 ID', width: 110, sortable: true },
                    { key: 'publishedAt', label: '发布时间', minWidth: 180, sortable: true },
                ]"
            >
                <template #cell-versionNumber="{ row }">v{{ row.versionNumber }}</template>
            </AmDataTable>
            <el-empty v-if="!versionsLoading && versions.length === 0" description="尚未发布版本" />
        </el-drawer>
    </section>
</template>

<style scoped>
.designer-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 680px;
    background: #f8fafc;
    color: #0f172a;
}
.designer-header {
    z-index: 2;
    display: flex;
    flex: 0 0 72px;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    border-bottom: 1px solid #e2e8f0;
    background: #fff;
}
.designer-header__identity,
.designer-header__actions {
    display: flex;
    align-items: center;
    gap: 12px;
}
.designer-header h1 {
    margin: 0;
    font-size: 18px;
    font-weight: 650;
}
.designer-header p {
    margin: 3px 0 0;
    color: #64748b;
    font-size: 12px;
}
.designer-workspace {
    display: grid;
    min-height: 0;
    flex: 1;
    grid-template-columns: minmax(640px, 1fr) 300px;
}
@media (max-width: 1180px) {
    .designer-workspace {
        grid-template-columns: minmax(536px, 1fr) 260px;
    }
}
@media (max-width: 900px) {
    .designer-page {
        height: auto;
        min-height: 0;
    }
    .designer-header {
        align-items: flex-start;
        gap: 12px;
        padding: 12px;
    }
    .designer-header__actions {
        flex-wrap: wrap;
        justify-content: flex-end;
    }
    .designer-workspace {
        display: flex;
        flex-direction: column;
    }
}
</style>
