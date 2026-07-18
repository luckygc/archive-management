<script setup lang="ts">
import {
    ArrowLeft,
    Check,
    Connection,
    Delete,
    FullScreen,
    RefreshLeft,
    RefreshRight,
    ZoomIn,
    ZoomOut,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";

import {
    createApprovalWorkflowDefinition,
    getApprovalWorkflowDefinition,
    listApprovalWorkflowDefinitionVersions,
    publishApprovalWorkflowDefinition,
    updateApprovalWorkflowDefinition,
} from "@/shared/api/approval-workflow";
import { listAuthenticationUserOptions } from "@/shared/api/authentication";
import type {
    ApprovalConditionOperator,
    ApprovalFlowEdgeDto,
    ApprovalFlowNodeDto,
    ApprovalWorkflowDefinitionVersionDto,
    ApprovalWorkflowGraphDto,
} from "@/shared/types/approval-workflow";
import type { AuthenticationUserOptionDto } from "@/shared/types/authentication";

import ApprovalWorkflowDesigner from "./ApprovalWorkflowDesigner.vue";
import { createDefaultApprovalGraph, validateApprovalGraph } from "./approval-workflow-graph";

type DesignerExpose = InstanceType<typeof ApprovalWorkflowDesigner>;

const route = useRoute();
const router = useRouter();
const designer = ref<DesignerExpose>();
const definitionId = ref<number>();
const loading = ref(true);
const saving = ref(false);
const publishing = ref(false);
const dirty = ref(false);
const selected = ref<{ kind: "node" | "edge"; id: string }>();
const graph = ref<ApprovalWorkflowGraphDto>(createDefaultApprovalGraph());
const form = ref({ definitionCode: "", definitionName: "", businessType: "" });
const userOptions = ref<AuthenticationUserOptionDto[]>([]);
const versions = ref<ApprovalWorkflowDefinitionVersionDto[]>([]);
const versionsOpen = ref(false);
const versionsLoading = ref(false);

const issues = computed(() => validateApprovalGraph(graph.value));
const selectedNode = computed(() =>
    selected.value?.kind === "node"
        ? graph.value.nodes.find((item) => item.nodeCode === selected.value?.id)
        : undefined,
);
const selectedEdge = computed(() =>
    selected.value?.kind === "edge"
        ? graph.value.edges.find((item) => item.edgeCode === selected.value?.id)
        : undefined,
);
const selectedEdgeSource = computed(() =>
    graph.value.nodes.find((item) => item.nodeCode === selectedEdge.value?.sourceNodeCode),
);

function markDirty() {
    dirty.value = true;
}

function updateGraph(value: ApprovalWorkflowGraphDto) {
    graph.value = value;
    markDirty();
}

function updateNode(values: Partial<Pick<ApprovalFlowNodeDto, "nodeName" | "candidateUserIds">>) {
    if (!selectedNode.value) return;
    designer.value?.updateNode(selectedNode.value.nodeCode, values);
}

function updateEdge(values: Partial<ApprovalFlowEdgeDto>) {
    if (!selectedEdge.value) return;
    designer.value?.updateEdge(selectedEdge.value.edgeCode, values);
}

function setDefaultFlow(value: boolean) {
    if (!selectedEdge.value) return;
    if (value) {
        for (const edge of graph.value.edges.filter(
            (item) =>
                item.sourceNodeCode === selectedEdge.value?.sourceNodeCode &&
                item.edgeCode !== selectedEdge.value?.edgeCode,
        )) {
            designer.value?.updateEdge(edge.edgeCode, { defaultFlow: false });
        }
    }
    updateEdge({ defaultFlow: value, condition: value ? undefined : selectedEdge.value.condition });
}

function updateCondition(
    key: "field" | "operator" | "values",
    value: string | ApprovalConditionOperator | string[],
) {
    if (!selectedEdge.value) return;
    const current = selectedEdge.value.condition ?? { field: "", operator: "EQUALS", values: [] };
    updateEdge({ condition: { ...current, [key]: value } });
}

function metadataError() {
    if (!form.value.definitionCode.trim()) return "请填写定义编码";
    if (!/^[a-z][a-z0-9_-]{0,99}$/.test(form.value.definitionCode.trim()))
        return "定义编码需以小写字母开头，只能包含小写字母、数字、下划线或连字符";
    if (!form.value.definitionName.trim()) return "请填写定义名称";
    if (!form.value.businessType.trim()) return "请填写业务类型";
    return undefined;
}

async function save(showSuccess = true) {
    const error = metadataError();
    if (error) {
        ElMessage.warning(error);
        return false;
    }
    saving.value = true;
    try {
        const payload = {
            definitionCode: form.value.definitionCode.trim(),
            definitionName: form.value.definitionName.trim(),
            businessType: form.value.businessType.trim(),
            graph: graph.value,
        };
        if (definitionId.value) {
            await updateApprovalWorkflowDefinition(definitionId.value, payload);
        } else {
            const created = await createApprovalWorkflowDefinition(payload);
            definitionId.value = created.id;
            await router.replace({
                name: "approval-workflow-designer",
                params: { id: created.id },
            });
        }
        dirty.value = false;
        if (showSuccess) ElMessage.success("流程草稿已保存");
        return true;
    } catch (errorValue) {
        ElMessage.error((errorValue as Error).message);
        return false;
    } finally {
        saving.value = false;
    }
}

async function publish() {
    const error = metadataError();
    if (error) return ElMessage.warning(error);
    if (issues.value.length > 0) return ElMessage.warning("请先处理画布中的校验问题");
    try {
        await ElMessageBox.confirm(
            "发布后，新发起的流程使用新版本；运行中的流程继续使用原版本。确认发布？",
            "发布流程",
            { type: "warning", confirmButtonText: "保存并发布" },
        );
    } catch {
        return;
    }
    publishing.value = true;
    try {
        if (!(await save(false)) || !definitionId.value) return;
        const version = await publishApprovalWorkflowDefinition(definitionId.value);
        ElMessage.success(`流程已发布为版本 ${version.versionNumber}`);
    } catch (errorValue) {
        ElMessage.error((errorValue as Error).message);
    } finally {
        publishing.value = false;
    }
}

async function openVersions() {
    if (!definitionId.value) return ElMessage.info("保存草稿后即可查看版本");
    versionsOpen.value = true;
    versionsLoading.value = true;
    try {
        versions.value = (
            await listApprovalWorkflowDefinitionVersions(definitionId.value, { limit: 100 })
        ).items;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        versionsLoading.value = false;
    }
}

function focusIssue(elementId?: string) {
    if (elementId) designer.value?.focusElement(elementId);
}

function userLabel(userId: number) {
    const user = userOptions.value.find((item) => item.id === userId);
    return user ? `${user.displayName}（${user.username}）` : `用户 #${userId}`;
}

function beforeUnload(event: BeforeUnloadEvent) {
    if (!dirty.value) return;
    event.preventDefault();
    event.returnValue = "";
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    try {
        await ElMessageBox.confirm("当前流程还有未保存的修改，确认离开？", "未保存修改", {
            type: "warning",
            confirmButtonText: "放弃修改",
            cancelButtonText: "继续编辑",
        });
        return true;
    } catch {
        return false;
    }
});

onMounted(async () => {
    window.addEventListener("beforeunload", beforeUnload);
    try {
        const rawId = route.params.id;
        const id = rawId === "new" ? undefined : Number(rawId);
        const requests: Promise<unknown>[] = [
            listAuthenticationUserOptions(1000).then((response) => {
                userOptions.value = response.items;
            }),
        ];
        if (id && Number.isInteger(id) && id > 0) {
            requests.push(
                getApprovalWorkflowDefinition(id).then((definition) => {
                    definitionId.value = definition.id;
                    form.value = {
                        definitionCode: definition.definitionCode,
                        definitionName: definition.definitionName,
                        businessType: definition.businessType,
                    };
                    graph.value = definition.graph;
                }),
            );
        }
        await Promise.all(requests);
        dirty.value = false;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        loading.value = false;
    }
});

onBeforeUnmount(() => window.removeEventListener("beforeunload", beforeUnload));
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
            <aside class="designer-palette" aria-label="节点工具箱">
                <div class="panel-title">节点</div>
                <button
                    class="palette-item"
                    title="拖拽到画布；键盘按回车可直接添加"
                    @mousedown="designer?.startDrag('APPROVAL')"
                    @keydown.enter.prevent="designer?.addNode('APPROVAL')"
                    @keydown.space.prevent="designer?.addNode('APPROVAL')"
                >
                    <span class="palette-shape palette-shape--task" />
                    <span><strong>审批节点</strong><small>指定用户办理</small></span>
                </button>
                <button
                    class="palette-item"
                    title="拖拽到画布；键盘按回车可直接添加"
                    @mousedown="designer?.startDrag('EXCLUSIVE_GATEWAY')"
                    @keydown.enter.prevent="designer?.addNode('EXCLUSIVE_GATEWAY')"
                    @keydown.space.prevent="designer?.addNode('EXCLUSIVE_GATEWAY')"
                >
                    <span class="palette-shape palette-shape--gateway" />
                    <span><strong>条件分支</strong><small>按业务字段路由</small></span>
                </button>
                <div class="palette-help">
                    拖入节点后，从节点锚点拉出连线。开始与结束节点已自动创建。
                </div>
            </aside>

            <main class="designer-stage">
                <div class="canvas-toolbar" aria-label="画布工具栏">
                    <el-button-group>
                        <el-button
                            :icon="RefreshLeft"
                            title="撤销（Ctrl+Z）"
                            @click="designer?.undo()"
                        />
                        <el-button
                            :icon="RefreshRight"
                            title="重做（Ctrl+Y）"
                            @click="designer?.redo()"
                        />
                    </el-button-group>
                    <el-button-group>
                        <el-button :icon="ZoomOut" title="缩小" @click="designer?.zoomOut()" />
                        <el-button :icon="ZoomIn" title="放大" @click="designer?.zoomIn()" />
                        <el-button
                            :icon="FullScreen"
                            title="适应画布"
                            @click="designer?.fitView()"
                        />
                    </el-button-group>
                    <el-button
                        :icon="Delete"
                        title="删除选中元素"
                        @click="designer?.deleteSelected()"
                    />
                </div>
                <ApprovalWorkflowDesigner
                    v-if="!loading"
                    ref="designer"
                    :graph="graph"
                    @change="updateGraph"
                    @select="selected = $event"
                />
            </main>

            <aside class="designer-properties" aria-label="属性面板">
                <template v-if="selectedNode">
                    <div class="panel-title">节点属性</div>
                    <el-form label-position="top">
                        <el-form-item label="节点编码"
                            ><el-input :model-value="selectedNode.nodeCode" disabled
                        /></el-form-item>
                        <el-form-item label="节点名称">
                            <el-input
                                :model-value="selectedNode.nodeName"
                                maxlength="100"
                                @update:model-value="updateNode({ nodeName: String($event) })"
                            />
                        </el-form-item>
                        <el-form-item
                            v-if="selectedNode.nodeType === 'APPROVAL'"
                            label="候选用户"
                            required
                        >
                            <el-select
                                :model-value="selectedNode.candidateUserIds"
                                multiple
                                filterable
                                collapse-tags
                                :max-collapse-tags="2"
                                placeholder="请选择办理人"
                                @update:model-value="
                                    updateNode({ candidateUserIds: $event as number[] })
                                "
                            >
                                <el-option
                                    v-for="user in userOptions"
                                    :key="user.id"
                                    :label="userLabel(user.id)"
                                    :value="user.id"
                                />
                            </el-select>
                        </el-form-item>
                    </el-form>
                </template>
                <template v-else-if="selectedEdge">
                    <div class="panel-title">连线属性</div>
                    <el-form label-position="top">
                        <el-form-item label="连线编码"
                            ><el-input :model-value="selectedEdge.edgeCode" disabled
                        /></el-form-item>
                        <template v-if="selectedEdgeSource?.nodeType === 'EXCLUSIVE_GATEWAY'">
                            <el-form-item label="默认分支">
                                <el-switch
                                    :model-value="selectedEdge.defaultFlow"
                                    @update:model-value="setDefaultFlow(Boolean($event))"
                                />
                            </el-form-item>
                            <template v-if="!selectedEdge.defaultFlow">
                                <el-form-item label="业务字段" required>
                                    <el-input
                                        :model-value="selectedEdge.condition?.field"
                                        placeholder="例如 archive_type"
                                        @update:model-value="
                                            updateCondition('field', String($event))
                                        "
                                    />
                                </el-form-item>
                                <el-form-item label="运算符" required>
                                    <el-select
                                        :model-value="selectedEdge.condition?.operator ?? 'EQUALS'"
                                        @update:model-value="
                                            updateCondition(
                                                'operator',
                                                $event as ApprovalConditionOperator,
                                            )
                                        "
                                    >
                                        <el-option label="等于" value="EQUALS" />
                                        <el-option label="不等于" value="NOT_EQUALS" />
                                        <el-option label="属于任一值" value="IN" />
                                    </el-select>
                                </el-form-item>
                                <el-form-item label="比较值" required>
                                    <el-select
                                        :model-value="selectedEdge.condition?.values ?? []"
                                        multiple
                                        filterable
                                        allow-create
                                        default-first-option
                                        placeholder="输入后回车，可添加多个"
                                        @update:model-value="
                                            updateCondition('values', $event as string[])
                                        "
                                    />
                                </el-form-item>
                            </template>
                        </template>
                        <el-alert
                            v-else
                            type="info"
                            :closable="false"
                            title="普通连线无需配置条件"
                        />
                    </el-form>
                </template>
                <template v-else>
                    <div class="panel-title">流程信息</div>
                    <el-form label-position="top">
                        <el-form-item label="定义编码" required>
                            <el-input
                                v-model="form.definitionCode"
                                :disabled="Boolean(definitionId)"
                                placeholder="例如 archive_intake_flow"
                                @input="markDirty"
                            />
                        </el-form-item>
                        <el-form-item label="定义名称" required
                            ><el-input
                                v-model="form.definitionName"
                                maxlength="100"
                                @input="markDirty"
                        /></el-form-item>
                        <el-form-item label="业务类型" required
                            ><el-input
                                v-model="form.businessType"
                                placeholder="例如 archive_intake"
                                @input="markDirty"
                        /></el-form-item>
                    </el-form>
                </template>

                <div class="issue-panel">
                    <div class="panel-title">
                        发布检查
                        <el-tag :type="issues.length ? 'warning' : 'success'" size="small">{{
                            issues.length
                        }}</el-tag>
                    </div>
                    <el-empty
                        v-if="issues.length === 0"
                        :image-size="42"
                        description="流程结构完整"
                    />
                    <button
                        v-for="(issue, index) in issues"
                        v-else
                        :key="`${issue.elementId}-${index}`"
                        class="issue-item"
                        @click="focusIssue(issue.elementId)"
                    >
                        <Connection /> <span>{{ issue.message }}</span>
                    </button>
                </div>
            </aside>
        </div>

        <el-drawer v-model="versionsOpen" title="发布版本" size="520px">
            <el-table v-loading="versionsLoading" :data="versions" row-key="id">
                <el-table-column prop="versionNumber" label="版本" width="90">
                    <template #default="{ row }">v{{ row.versionNumber }}</template>
                </el-table-column>
                <el-table-column prop="publishedBy" label="发布人 ID" width="110" />
                <el-table-column prop="publishedAt" label="发布时间" min-width="180" />
            </el-table>
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
    grid-template-columns: 220px minmax(420px, 1fr) 300px;
}

.designer-palette,
.designer-properties {
    overflow: auto;
    padding: 18px 16px;
    background: #fff;
}

.designer-palette {
    border-right: 1px solid #e2e8f0;
}
.designer-properties {
    border-left: 1px solid #e2e8f0;
}

.panel-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
    color: #334155;
    font-size: 13px;
    font-weight: 650;
}

.palette-item,
.issue-item {
    width: 100%;
    border: 1px solid #e2e8f0;
    background: #fff;
    color: #0f172a;
    cursor: pointer;
    text-align: left;
}

.palette-item {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
    padding: 12px;
    border-radius: 8px;
}

.palette-item:hover {
    border-color: #93c5fd;
    background: #f8fbff;
}
.palette-item strong {
    display: block;
    font-size: 13px;
}
.palette-item small {
    display: block;
    margin-top: 3px;
    color: #64748b;
}

.palette-shape {
    display: block;
    flex: 0 0 auto;
}
.palette-shape--task {
    width: 34px;
    height: 24px;
    border: 2px solid #2563eb;
    border-radius: 6px;
    background: #eff6ff;
}
.palette-shape--gateway {
    width: 25px;
    height: 25px;
    margin: 4px;
    transform: rotate(45deg);
    border: 2px solid #ea580c;
    background: #fff7ed;
}

.palette-help {
    margin-top: 18px;
    padding: 12px;
    border: 1px solid #dbeafe;
    background: #f8fafc;
    color: #64748b;
    font-size: 12px;
    line-height: 1.65;
}

.designer-stage {
    position: relative;
    min-width: 0;
    min-height: 0;
    overflow: hidden;
}
.canvas-toolbar {
    position: absolute;
    z-index: 3;
    top: 14px;
    left: 50%;
    display: flex;
    gap: 8px;
    transform: translateX(-50%);
    padding: 6px;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    background: rgb(255 255 255 / 94%);
    box-shadow: 0 4px 14px rgb(15 23 42 / 8%);
}
.issue-panel {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid #e2e8f0;
}
.issue-item {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    margin-bottom: 8px;
    padding: 9px 10px;
    border-color: #fed7aa;
    border-radius: 6px;
    background: #fff7ed;
    color: #9a3412;
    font-size: 12px;
    line-height: 1.45;
}
.issue-item svg {
    width: 15px;
    flex: 0 0 auto;
    margin-top: 1px;
}
:deep(.el-select) {
    width: 100%;
}

@media (max-width: 1180px) {
    .designer-workspace {
        grid-template-columns: 176px minmax(360px, 1fr) 260px;
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
    .designer-palette {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 8px;
        border-right: 0;
        border-bottom: 1px solid #e2e8f0;
    }
    .designer-palette .panel-title,
    .palette-help {
        grid-column: 1 / -1;
    }
    .designer-stage {
        height: 560px;
    }
    .designer-properties {
        border-top: 1px solid #e2e8f0;
        border-left: 0;
    }
}
</style>
