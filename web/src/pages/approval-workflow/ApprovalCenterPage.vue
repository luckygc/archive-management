<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref } from "vue";

import {
    approveApprovalWorkflowTask,
    getApprovalWorkflowInstance,
    listApprovalWorkflowDefinitionOptions,
    listMyApprovalWorkflowInstances,
    rejectApprovalWorkflowTask,
    startApprovalWorkflowInstance,
    withdrawApprovalWorkflowInstance,
} from "@/shared/api/approval-workflow";
import { listMyUnifiedTodos } from "@/shared/api/unified-todo";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import type {
    ApprovalAction,
    ApprovalInstanceStatus,
    ApprovalTaskStatus,
    ApprovalWorkflowDefinitionOptionDto,
    ApprovalWorkflowInstanceDetailDto,
    ApprovalWorkflowInstanceDto,
} from "@/shared/types/approval-workflow";
import type { UnifiedTodoDto } from "@/shared/types/unified-todo";
import { usePermissionStore } from "@/stores/permissionStore";

type TabName = "pending" | "started" | "completed";
type TaskAction = "approve" | "reject";

const instanceStatusLabels: Record<ApprovalInstanceStatus, string> = {
    RUNNING: "进行中",
    APPROVED: "已通过",
    REJECTED: "已驳回",
    WITHDRAWN: "已撤回",
    TERMINATED: "已终止",
};
const taskStatusLabels: Record<ApprovalTaskStatus, string> = {
    PENDING: "待办理",
    APPROVED: "已同意",
    REJECTED: "已驳回",
    WITHDRAWN: "已撤回",
    TERMINATED: "已终止",
};
const actionLabels: Record<ApprovalAction, string> = {
    APPROVE: "同意",
    REJECT: "驳回",
    WITHDRAW: "撤回",
    TERMINATE: "终止",
};

const permissionStore = usePermissionStore();
const activeTab = ref<TabName>("pending");
const loading = ref(false);
const pendingTasks = ref<UnifiedTodoDto[]>([]);
const completedTasks = ref<UnifiedTodoDto[]>([]);
const startedInstances = ref<ApprovalWorkflowInstanceDto[]>([]);
const pendingPrev = ref<string>();
const pendingNext = ref<string>();
const completedPrev = ref<string>();
const completedNext = ref<string>();
const startedPrev = ref<string>();
const startedNext = ref<string>();
const limit = ref(100);

const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<ApprovalWorkflowInstanceDetailDto>();
const startDialogOpen = ref(false);
const startSubmitting = ref(false);
const definitionOptions = ref<ApprovalWorkflowDefinitionOptionDto[]>([]);
const startForm = ref({ definitionId: undefined as number | undefined, businessId: "", title: "" });
const taskDialogOpen = ref(false);
const taskSubmitting = ref(false);
const selectedTask = ref<UnifiedTodoDto>();
const taskAction = ref<TaskAction>("approve");
const taskComment = ref("");
const selectedDefinition = computed(() =>
    definitionOptions.value.find((item) => item.id === startForm.value.definitionId),
);

async function loadTab(tab: TabName = activeTab.value, cursor?: string) {
    loading.value = true;
    try {
        if (tab === "started") {
            const response = await listMyApprovalWorkflowInstances({ limit: limit.value, cursor });
            startedInstances.value = response.items;
            startedPrev.value = response.prev;
            startedNext.value = response.next;
        } else {
            const completed = tab === "completed";
            const response = await listMyUnifiedTodos({
                completed,
                limit: limit.value,
                cursor,
            });
            if (completed) {
                completedTasks.value = response.items;
                completedPrev.value = response.prev;
                completedNext.value = response.next;
            } else {
                pendingTasks.value = response.items;
                pendingPrev.value = response.prev;
                pendingNext.value = response.next;
            }
        }
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        loading.value = false;
    }
}

async function openStartDialog() {
    try {
        definitionOptions.value = (await listApprovalWorkflowDefinitionOptions()).items;
        startForm.value = { definitionId: definitionOptions.value[0]?.id, businessId: "", title: "" };
        startDialogOpen.value = true;
    } catch (error) {
        ElMessage.error((error as Error).message);
    }
}

async function submitStart() {
    const definition = selectedDefinition.value;
    if (!definition || !startForm.value.businessId.trim() || !startForm.value.title.trim()) {
        return ElMessage.warning("请选择审批流并填写业务标识和标题");
    }
    startSubmitting.value = true;
    try {
        await startApprovalWorkflowInstance({
            definitionId: definition.id,
            businessType: definition.businessType,
            businessId: startForm.value.businessId.trim(),
            title: startForm.value.title.trim(),
        });
        ElMessage.success("审批流程已发起");
        startDialogOpen.value = false;
        activeTab.value = "started";
        await loadTab("started");
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        startSubmitting.value = false;
    }
}

function openTaskAction(value: unknown, action: TaskAction) {
    const row = value as UnifiedTodoDto;
    selectedTask.value = row;
    taskAction.value = action;
    taskComment.value = "";
    taskDialogOpen.value = true;
}

async function submitTaskAction() {
    if (!selectedTask.value) return;
    taskSubmitting.value = true;
    try {
        if (taskAction.value === "approve") {
            await approveApprovalWorkflowTask(selectedTask.value.id, taskComment.value.trim() || undefined);
        } else {
            await rejectApprovalWorkflowTask(selectedTask.value.id, taskComment.value.trim() || undefined);
        }
        ElMessage.success(taskAction.value === "approve" ? "审批任务已同意" : "审批任务已驳回");
        taskDialogOpen.value = false;
        await Promise.all([loadTab("pending"), loadTab("completed")]);
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        taskSubmitting.value = false;
    }
}

async function openDetail(instanceId: number) {
    detailOpen.value = true;
    detailLoading.value = true;
    detail.value = undefined;
    try {
        detail.value = await getApprovalWorkflowInstance(instanceId);
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        detailLoading.value = false;
    }
}

function instanceIdFromTodo(todo: UnifiedTodoDto) {
    const match = /[?&]instanceId=(\d+)/.exec(todo.sourcePath);
    return match?.[1] ? Number(match[1]) : undefined;
}

function openTodoDetail(value: unknown) {
    const instanceId = instanceIdFromTodo(value as UnifiedTodoDto);
    if (!instanceId) return ElMessage.warning("该待办暂未提供流程详情入口");
    void openDetail(instanceId);
}

async function withdraw(value: unknown) {
    const row = value as ApprovalWorkflowInstanceDto;
    try {
        await ElMessageBox.confirm("撤回后当前待办将关闭，且不能继续办理。确认撤回？", "撤回审批", {
            type: "warning",
        });
        await withdrawApprovalWorkflowInstance(row.id);
        ElMessage.success("审批流程已撤回");
        await loadTab("started");
    } catch (error) {
        if (error !== "cancel" && error !== "close") ElMessage.error((error as Error).message);
    }
}

function changeLimit(value: number) {
    limit.value = value;
    void loadTab(activeTab.value);
}

function formatTime(value?: string) {
    return value ? value.replace("T", " ").slice(0, 19) : "-";
}

function instanceStatusLabel(status: unknown) {
    return instanceStatusLabels[status as ApprovalInstanceStatus];
}

function taskStatusLabel(status: unknown) {
    return taskStatusLabels[status as ApprovalTaskStatus];
}

function actionLabel(action: unknown) {
    return actionLabels[action as ApprovalAction];
}

function instanceTagType(status: unknown) {
    const value = status as ApprovalInstanceStatus;
    if (value === "APPROVED") return "success";
    if (value === "REJECTED" || value === "TERMINATED") return "danger";
    if (value === "RUNNING") return "primary";
    return "info";
}

onMounted(() => void loadTab());
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>审批中心</h1>
            <el-button
                v-if="permissionStore.has('approval:instance:start')"
                type="primary"
                @click="openStartDialog"
                >发起审批</el-button
            >
        </div>
        <el-card shadow="never">
            <el-tabs v-model="activeTab" @tab-change="(name) => loadTab(name as TabName)">
                <el-tab-pane label="我的待办" name="pending">
                    <el-table v-loading="loading" :data="pendingTasks" row-key="id">
                        <el-table-column prop="title" label="标题" min-width="220" />
                        <el-table-column prop="businessType" label="业务类型" width="140" />
                        <el-table-column prop="businessId" label="业务标识" width="160" />
                        <el-table-column prop="nodeName" label="当前节点" width="150" />
                        <el-table-column label="到达时间" width="180">
                            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
                        </el-table-column>
                        <el-table-column label="操作" width="210" fixed="right">
                            <template #default="{ row }">
                                <el-button link @click="openTodoDetail(row)">详情</el-button>
                                <el-button link type="primary" @click="openTaskAction(row, 'approve')">同意</el-button>
                                <el-button link type="danger" @click="openTaskAction(row, 'reject')">驳回</el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                    <CursorPagination
                        :limit="limit"
                        :prev="pendingPrev"
                        :next="pendingNext"
                        :loading="loading"
                        @page="(cursor) => loadTab('pending', cursor)"
                        @limit-change="changeLimit"
                    />
                </el-tab-pane>
                <el-tab-pane label="我发起的" name="started">
                    <el-table v-loading="loading" :data="startedInstances" row-key="id">
                        <el-table-column prop="title" label="标题" min-width="220" />
                        <el-table-column prop="businessType" label="业务类型" width="140" />
                        <el-table-column prop="businessId" label="业务标识" width="160" />
                        <el-table-column label="状态" width="110">
                            <template #default="{ row }">
                                <el-tag :type="instanceTagType(row.status)">{{ instanceStatusLabel(row.status) }}</el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="currentNodeName" label="当前节点" width="150" />
                        <el-table-column label="发起时间" width="180">
                            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
                        </el-table-column>
                        <el-table-column label="操作" width="140" fixed="right">
                            <template #default="{ row }">
                                <el-button link @click="openDetail(row.id)">详情</el-button>
                                <el-button v-if="row.status === 'RUNNING'" link type="danger" @click="withdraw(row)">撤回</el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                    <CursorPagination
                        :limit="limit"
                        :prev="startedPrev"
                        :next="startedNext"
                        :loading="loading"
                        @page="(cursor) => loadTab('started', cursor)"
                        @limit-change="changeLimit"
                    />
                </el-tab-pane>
                <el-tab-pane label="已办" name="completed">
                    <el-table v-loading="loading" :data="completedTasks" row-key="id">
                        <el-table-column prop="title" label="标题" min-width="220" />
                        <el-table-column prop="businessType" label="业务类型" width="140" />
                        <el-table-column prop="businessId" label="业务标识" width="160" />
                        <el-table-column prop="nodeName" label="办理节点" width="150" />
                        <el-table-column label="结果" width="110">
                            <template #default>已完成</template>
                        </el-table-column>
                        <el-table-column label="办理时间" width="180">
                            <template #default="{ row }">{{ formatTime(row.completedAt) }}</template>
                        </el-table-column>
                        <el-table-column label="操作" width="80" fixed="right">
                            <template #default="{ row }"><el-button link @click="openTodoDetail(row)">详情</el-button></template>
                        </el-table-column>
                    </el-table>
                    <CursorPagination
                        :limit="limit"
                        :prev="completedPrev"
                        :next="completedNext"
                        :loading="loading"
                        @page="(cursor) => loadTab('completed', cursor)"
                        @limit-change="changeLimit"
                    />
                </el-tab-pane>
            </el-tabs>
        </el-card>

        <el-dialog v-model="startDialogOpen" title="发起审批" width="560px">
            <el-form label-width="90px">
                <el-form-item label="审批流" required>
                    <el-select v-model="startForm.definitionId">
                        <el-option
                            v-for="option in definitionOptions"
                            :key="option.id"
                            :label="`${option.definitionName}（${option.businessType}）`"
                            :value="option.id"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="业务标识" required><el-input v-model="startForm.businessId" /></el-form-item>
                <el-form-item label="标题" required><el-input v-model="startForm.title" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button :disabled="startSubmitting" @click="startDialogOpen = false">取消</el-button>
                <el-button type="primary" :loading="startSubmitting" @click="submitStart">发起</el-button>
            </template>
        </el-dialog>

        <el-dialog
            v-model="taskDialogOpen"
            :title="taskAction === 'approve' ? '同意审批' : '驳回审批'"
            width="520px"
        >
            <el-form label-width="80px">
                <el-form-item label="审批意见">
                    <el-input v-model="taskComment" type="textarea" :rows="4" maxlength="2000" show-word-limit />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button :disabled="taskSubmitting" @click="taskDialogOpen = false">取消</el-button>
                <el-button
                    :type="taskAction === 'approve' ? 'primary' : 'danger'"
                    :loading="taskSubmitting"
                    @click="submitTaskAction"
                    >确认{{ taskAction === "approve" ? "同意" : "驳回" }}</el-button
                >
            </template>
        </el-dialog>

        <el-drawer v-model="detailOpen" title="审批详情" size="760px">
            <div v-loading="detailLoading">
                <template v-if="detail">
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="标题">{{ detail.instance.title }}</el-descriptions-item>
                        <el-descriptions-item label="状态">
                            <el-tag :type="instanceTagType(detail.instance.status)">{{ instanceStatusLabel(detail.instance.status) }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="业务类型">{{ detail.instance.businessType }}</el-descriptions-item>
                        <el-descriptions-item label="业务标识">{{ detail.instance.businessId }}</el-descriptions-item>
                        <el-descriptions-item label="发起时间">{{ formatTime(detail.instance.createdAt) }}</el-descriptions-item>
                        <el-descriptions-item label="完成时间">{{ formatTime(detail.instance.completedAt) }}</el-descriptions-item>
                    </el-descriptions>
                    <h3>流程节点</h3>
                    <el-table :data="detail.tasks" row-key="id" border>
                        <el-table-column prop="nodeName" label="节点" />
                        <el-table-column label="状态" width="110">
                            <template #default="{ row }">{{ taskStatusLabel(row.status) }}</template>
                        </el-table-column>
                        <el-table-column label="到达时间" width="180">
                            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
                        </el-table-column>
                        <el-table-column label="完成时间" width="180">
                            <template #default="{ row }">{{ formatTime(row.completedAt) }}</template>
                        </el-table-column>
                    </el-table>
                    <h3>审批意见</h3>
                    <el-empty v-if="detail.opinions.length === 0" description="暂无审批意见" />
                    <el-table v-else :data="detail.opinions" row-key="id" border>
                        <el-table-column label="动作" width="100">
                            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
                        </el-table-column>
                        <el-table-column prop="operatorUserId" label="办理人 ID" width="110" />
                        <el-table-column prop="comment" label="意见" />
                        <el-table-column label="时间" width="180">
                            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
                        </el-table-column>
                    </el-table>
                </template>
            </div>
        </el-drawer>
    </section>
</template>

<style scoped>
h3 {
    margin: 20px 0 12px;
    font-size: 16px;
}
</style>
