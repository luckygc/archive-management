<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";

import {
    assignArchiveFondsNumber,
    closeArchiveFonds,
    createArchiveFonds,
    listArchiveFonds,
    listArchiveFondsEvents,
    reopenArchiveFonds,
    updateArchiveFonds,
} from "@/shared/api/archive-metadata";
import { AmDataTable } from "@/shared/components/data-table";
import { requestErrorMessage } from "@/shared/requestError";
import type {
    ArchiveFondsDto,
    ArchiveFondsEventDto,
    ArchiveFondsEventType,
    ArchiveFondsStatus,
} from "@/shared/types/archive-metadata";

const fonds = ref<ArchiveFondsDto[]>([]);
const loading = ref(false);
const saving = ref(false);
const statusFilter = ref<ArchiveFondsStatus>();

const editorOpen = ref(false);
const editingId = ref<number>();
const editorForm = reactive({
    fondsCode: "",
    fondsName: "",
    startDate: "",
    endDate: "",
    historyNote: "",
    sortOrder: 0,
});

const numberDialogOpen = ref(false);
const numberTarget = ref<ArchiveFondsDto>();
const numberForm = reactive({ fondsNo: "", assignedBy: "", reason: "", effectiveAt: "" });

const lifecycleDialogOpen = ref(false);
const lifecycleTarget = ref<ArchiveFondsDto>();
const lifecycleAction = ref<"close" | "reopen">("close");
const lifecycleForm = reactive({ reason: "", effectiveAt: "" });

const eventsOpen = ref(false);
const eventsLoading = ref(false);
const eventsTarget = ref<ArchiveFondsDto>();
const events = ref<ArchiveFondsEventDto[]>([]);

const eventTypeLabels: Record<ArchiveFondsEventType, string> = {
    NUMBER_ASSIGNED: "分配全宗号",
    CLOSED: "封闭",
    REOPENED: "重新开放",
};

async function loadFonds() {
    loading.value = true;
    try {
        fonds.value = (await listArchiveFonds(statusFilter.value)).items;
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "全宗加载失败"));
    } finally {
        loading.value = false;
    }
}

function openCreate() {
    editingId.value = undefined;
    Object.assign(editorForm, {
        fondsCode: "",
        fondsName: "",
        startDate: "",
        endDate: "",
        historyNote: "",
        sortOrder: 0,
    });
    editorOpen.value = true;
}

function openEdit(row: ArchiveFondsDto) {
    editingId.value = row.id;
    Object.assign(editorForm, {
        fondsCode: row.fondsCode,
        fondsName: row.fondsName,
        startDate: row.startDate ?? "",
        endDate: row.endDate ?? "",
        historyNote: row.historyNote ?? "",
        sortOrder: row.sortOrder,
    });
    editorOpen.value = true;
}

async function saveFonds() {
    if (!editorForm.fondsName.trim() || (!editingId.value && !editorForm.fondsCode.trim())) {
        return ElMessage.warning("请填写系统编码和全宗名称");
    }
    if (editorForm.startDate && editorForm.endDate && editorForm.endDate < editorForm.startDate) {
        return ElMessage.warning("终止日期不能早于起始日期");
    }
    saving.value = true;
    try {
        const common = {
            fondsName: editorForm.fondsName.trim(),
            startDate: editorForm.startDate || undefined,
            endDate: editorForm.endDate || undefined,
            historyNote: editorForm.historyNote.trim() || undefined,
            sortOrder: editorForm.sortOrder,
        };
        const updated = editingId.value
            ? await updateArchiveFonds(editingId.value, common)
            : await createArchiveFonds({ fondsCode: editorForm.fondsCode.trim(), ...common });
        replaceFonds(updated);
        editorOpen.value = false;
        ElMessage.success(editingId.value ? "全宗信息已更新" : "全宗已创建");
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "全宗保存失败"));
    } finally {
        saving.value = false;
    }
}

function openAssignNumber(row: ArchiveFondsDto) {
    numberTarget.value = row;
    Object.assign(numberForm, { fondsNo: "", assignedBy: "", reason: "", effectiveAt: "" });
    numberDialogOpen.value = true;
}

async function saveNumber() {
    if (!numberTarget.value || !numberForm.fondsNo.trim() || !numberForm.reason.trim()) {
        return ElMessage.warning("请填写全宗号和分配原因");
    }
    saving.value = true;
    try {
        const updated = await assignArchiveFondsNumber(numberTarget.value.id, {
            fondsNo: numberForm.fondsNo.trim(),
            assignedBy: numberForm.assignedBy.trim() || undefined,
            reason: numberForm.reason.trim(),
            effectiveAt: numberForm.effectiveAt || undefined,
        });
        replaceFonds(updated);
        numberDialogOpen.value = false;
        ElMessage.success("全宗号已分配");
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "全宗号分配失败"));
    } finally {
        saving.value = false;
    }
}

function openLifecycle(row: ArchiveFondsDto, action: "close" | "reopen") {
    lifecycleTarget.value = row;
    lifecycleAction.value = action;
    Object.assign(lifecycleForm, { reason: "", effectiveAt: "" });
    lifecycleDialogOpen.value = true;
}

async function saveLifecycle() {
    if (!lifecycleTarget.value || !lifecycleForm.reason.trim()) {
        return ElMessage.warning("请填写办理原因");
    }
    saving.value = true;
    try {
        const payload = {
            reason: lifecycleForm.reason.trim(),
            effectiveAt: lifecycleForm.effectiveAt || undefined,
        };
        const updated =
            lifecycleAction.value === "close"
                ? await closeArchiveFonds(lifecycleTarget.value.id, payload)
                : await reopenArchiveFonds(lifecycleTarget.value.id, payload);
        replaceFonds(updated);
        lifecycleDialogOpen.value = false;
        ElMessage.success(lifecycleAction.value === "close" ? "全宗已封闭" : "全宗已重新开放");
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "全宗状态办理失败"));
    } finally {
        saving.value = false;
    }
}

async function openEvents(row: ArchiveFondsDto) {
    eventsTarget.value = row;
    events.value = [];
    eventsOpen.value = true;
    eventsLoading.value = true;
    try {
        events.value = (await listArchiveFondsEvents(row.id)).items;
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "全宗事件加载失败"));
    } finally {
        eventsLoading.value = false;
    }
}

function replaceFonds(updated: ArchiveFondsDto) {
    const index = fonds.value.findIndex((item) => item.id === updated.id);
    if (index >= 0)
        fonds.value = fonds.value.map((item) => (item.id === updated.id ? updated : item));
    else fonds.value = [...fonds.value, updated];
}

function formatDateTime(value: string | null) {
    return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—";
}

onMounted(loadFonds);
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <div>
                <h1>全宗管理</h1>
                <el-text type="info"
                    >系统编码稳定不变；全宗号、封闭与重新开放均保留事件记录。</el-text
                >
            </div>
            <el-button type="primary" @click="openCreate">新建全宗</el-button>
        </div>

        <el-card shadow="never">
            <div class="fonds-toolbar">
                <el-select
                    v-model="statusFilter"
                    aria-label="全宗状态筛选"
                    clearable
                    placeholder="全部状态"
                    @change="() => loadFonds()"
                >
                    <el-option label="有效" value="ACTIVE" />
                    <el-option label="已封闭" value="CLOSED" />
                </el-select>
            </div>
            <AmDataTable
                :data="fonds"
                :loading="loading"
                row-key="id"
                empty-text="暂无全宗"
                :columns="[
                    { key: 'fondsCode', label: '系统编码', width: 130, sortable: true },
                    { key: 'fondsNo', label: '全宗号', width: 130, sortable: true },
                    { key: 'fondsName', label: '全宗名称', minWidth: 180, sortable: true },
                    { key: 'status', label: '状态', width: 100, sortable: true },
                    { key: 'dates', label: '起止日期', width: 210 },
                    { key: 'actions', label: '操作', width: 300, fixed: 'right' },
                ]"
            >
                <template #cell-fondsNo="{ row }">
                    <span v-if="row.fondsNo">{{ row.fondsNo }}</span>
                    <el-tag v-else type="info" effect="plain">未分配</el-tag>
                </template>
                <template #cell-status="{ row }">
                    <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                        {{ row.status === "ACTIVE" ? "有效" : "已封闭" }}
                    </el-tag>
                </template>
                <template #cell-dates="{ row }">
                    {{ row.startDate || "—" }} 至 {{ row.endDate || "—" }}
                </template>
                <template #cell-actions="{ row }">
                    <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                    <el-button
                        v-if="!row.fondsNo"
                        link
                        type="primary"
                        @click="openAssignNumber(row)"
                    >
                        分配全宗号
                    </el-button>
                    <el-button link type="primary" @click="openEvents(row)">事件</el-button>
                    <el-button
                        v-if="row.status === 'ACTIVE'"
                        link
                        type="danger"
                        @click="openLifecycle(row, 'close')"
                    >
                        封闭
                    </el-button>
                    <el-button v-else link type="primary" @click="openLifecycle(row, 'reopen')">
                        重新开放
                    </el-button>
                </template>
            </AmDataTable>
        </el-card>

        <el-dialog
            v-model="editorOpen"
            :title="editingId ? '编辑全宗信息' : '新建全宗'"
            width="600px"
            destroy-on-close
        >
            <el-form :model="editorForm" label-position="top">
                <el-row :gutter="16">
                    <el-col :span="12">
                        <el-form-item label="系统全宗编码" required>
                            <el-input
                                v-model="editorForm.fondsCode"
                                :disabled="Boolean(editingId)"
                                placeholder="创建后不可修改或复用"
                            />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="全宗名称" required>
                            <el-input v-model="editorForm.fondsName" />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row :gutter="16">
                    <el-col :span="12">
                        <el-form-item label="起始日期">
                            <el-date-picker
                                v-model="editorForm.startDate"
                                type="date"
                                value-format="YYYY-MM-DD"
                            />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="终止日期">
                            <el-date-picker
                                v-model="editorForm.endDate"
                                type="date"
                                value-format="YYYY-MM-DD"
                            />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-form-item label="立档单位及全宗沿革">
                    <el-input v-model="editorForm.historyNote" type="textarea" :rows="4" />
                </el-form-item>
                <el-form-item label="排序">
                    <el-input-number v-model="editorForm.sortOrder" :min="0" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editorOpen = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="saveFonds">保存</el-button>
            </template>
        </el-dialog>

        <el-dialog v-model="numberDialogOpen" title="分配全宗号" width="520px" destroy-on-close>
            <el-alert
                type="warning"
                :closable="false"
                title="全宗号分配后不能通过普通编辑修改。"
                show-icon
            />
            <el-form :model="numberForm" label-position="top" class="dialog-form">
                <el-form-item label="业务全宗号" required>
                    <el-input v-model="numberForm.fondsNo" />
                </el-form-item>
                <el-form-item label="分配机关">
                    <el-input v-model="numberForm.assignedBy" />
                </el-form-item>
                <el-form-item label="分配原因" required>
                    <el-input v-model="numberForm.reason" type="textarea" :rows="3" />
                </el-form-item>
                <el-form-item label="生效时间">
                    <el-date-picker
                        v-model="numberForm.effectiveAt"
                        type="datetime"
                        value-format="YYYY-MM-DDTHH:mm:ss"
                        placeholder="默认立即生效"
                    />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="numberDialogOpen = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="saveNumber">确认分配</el-button>
            </template>
        </el-dialog>

        <el-dialog
            v-model="lifecycleDialogOpen"
            :title="lifecycleAction === 'close' ? '封闭全宗' : '重新开放全宗'"
            width="500px"
            destroy-on-close
        >
            <el-alert
                v-if="lifecycleAction === 'close'"
                type="warning"
                :closable="false"
                title="封闭后历史数据仍可查询，但不能继续写入。"
                show-icon
            />
            <el-form :model="lifecycleForm" label-position="top" class="dialog-form">
                <el-form-item label="办理原因" required>
                    <el-input v-model="lifecycleForm.reason" type="textarea" :rows="3" />
                </el-form-item>
                <el-form-item label="生效时间">
                    <el-date-picker
                        v-model="lifecycleForm.effectiveAt"
                        type="datetime"
                        value-format="YYYY-MM-DDTHH:mm:ss"
                        placeholder="默认立即生效"
                    />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="lifecycleDialogOpen = false">取消</el-button>
                <el-button
                    :type="lifecycleAction === 'close' ? 'danger' : 'primary'"
                    :loading="saving"
                    @click="saveLifecycle"
                >
                    确认{{ lifecycleAction === "close" ? "封闭" : "开放" }}
                </el-button>
            </template>
        </el-dialog>

        <el-drawer
            v-model="eventsOpen"
            :title="`${eventsTarget?.fondsName ?? ''} · 全宗事件`"
            size="680px"
            destroy-on-close
        >
            <AmDataTable
                :data="events"
                :loading="eventsLoading"
                row-key="id"
                empty-text="暂无事件记录"
                :columns="[
                    { key: 'eventType', label: '事件', width: 130 },
                    { key: 'effectiveAt', label: '生效时间', width: 180 },
                    { key: 'change', label: '变更', minWidth: 170 },
                    { key: 'reason', label: '原因', minWidth: 180 },
                ]"
            >
                <template #cell-eventType="{ row }">{{ eventTypeLabels[row.eventType] }}</template>
                <template #cell-effectiveAt="{ row }">{{
                    formatDateTime(row.effectiveAt)
                }}</template>
                <template #cell-change="{ row }">
                    {{ row.previousValue || "—" }} → {{ row.currentValue || "—" }}
                </template>
            </AmDataTable>
        </el-drawer>
    </section>
</template>

<style scoped>
.fonds-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 12px;
}

.fonds-toolbar :deep(.el-select) {
    width: 160px;
}

.dialog-form {
    margin-top: 16px;
}

:deep(.el-date-editor) {
    width: 100%;
}
</style>
