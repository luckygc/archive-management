<script setup lang="ts">
import { HttpClientError } from "@archive-management/frontend-core/api";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";

import {
    createArchiveRuntimeDefinition,
    deleteArchiveRuntimeDefinition,
    disableArchiveRuntimeDefinition,
    enableArchiveRuntimeDefinition,
    exportArchiveRuntimeSnapshot,
    getArchiveRuntimeFields,
    importArchiveRuntimeSnapshot,
    listArchiveRuntimeDefinitions,
    preflightArchiveRuntimeSnapshot,
    publishArchiveRuntimeDefinition,
    restoreArchiveRuntimeSnapshot,
    simulateArchiveRuntimeDefinitions,
    updateArchiveRuntimeDefinition,
} from "@/shared/api/archive-rules";
import { requestErrorMessage } from "@/shared/requestError";
import type {
    ArchiveRuntimeActionType,
    ArchiveRuntimeDefinitionDto,
    ArchiveRuntimeDefinitionKind,
    ArchiveRuntimeDefinitionRequest,
    ArchiveRuntimeExecutionResult,
    ArchiveRuntimeFieldDto,
    ArchiveRuntimeSnapshot,
    ArchiveRuntimeSnapshotPreflightRequest,
    ArchiveRuntimeSnapshotPreflightResult,
    ArchiveRuntimeStatus,
    ArchiveRuntimeTriggerPoint,
} from "@/shared/types/archive-rules";

const triggerPoints: Array<{ value: ArchiveRuntimeTriggerPoint; label: string }> = [
    { value: "ITEM_BEFORE_CREATE", label: "条目创建前" },
    { value: "ITEM_BEFORE_UPDATE", label: "条目修改前" },
    { value: "ITEM_BEFORE_DELETE", label: "条目删除前" },
    { value: "VOLUME_BEFORE_CREATE", label: "案卷创建前" },
    { value: "VOLUME_BEFORE_ADD_ITEM", label: "条目入卷前" },
    { value: "FILE_BEFORE_UPLOAD", label: "电子文件上传前" },
    { value: "EXPORT_BEFORE_CREATE", label: "导出文件生成前" },
];
const triggerLabels = Object.fromEntries(triggerPoints.map((item) => [item.value, item.label]));
const assignmentTriggers = new Set<ArchiveRuntimeTriggerPoint>([
    "ITEM_BEFORE_CREATE",
    "ITEM_BEFORE_UPDATE",
    "VOLUME_BEFORE_CREATE",
    "VOLUME_BEFORE_ADD_ITEM",
]);

const route = useRoute();
const routeVersionId = Number(route.query.schemeVersionId);
const schemeVersionId = ref<number | undefined>(
    Number.isSafeInteger(routeVersionId) && routeVersionId > 0 ? routeVersionId : undefined,
);
const status = ref<ArchiveRuntimeStatus>();
const triggerPoint = ref<ArchiveRuntimeTriggerPoint>();
const definitions = ref<ArchiveRuntimeDefinitionDto[]>([]);
const loading = ref(false);
const loadError = ref<string>();

const editorOpen = ref(false);
const editorSubmitting = ref(false);
const editingId = ref<number>();
const editorError = ref<string>();
const editorViolations = ref<Array<{ field: string; message: string }>>([]);
const fieldLoading = ref(false);
const fieldCatalog = ref<ArchiveRuntimeFieldDto[]>([]);
const editor = ref(defaultEditor());

const simulationOpen = ref(false);
const simulationSubmitting = ref(false);
const simulationError = ref<string>();
const simulationResult = ref<ArchiveRuntimeExecutionResult>();
const simulation = ref(defaultSimulation());

const snapshotOpen = ref(false);
const snapshotSubmitting = ref(false);
const snapshotError = ref<string>();
const snapshotJson = ref("");
const snapshotTargetSchemeCode = ref("");
const snapshotTargetVersionCode = ref("");
const categoryMappingsJson = ref("{}");
const fieldMappingsJson = ref("{}");
const preflightResult = ref<ArchiveRuntimeSnapshotPreflightResult>();

const availableActions = computed<ArchiveRuntimeActionType[]>(() =>
    assignmentTriggers.has(editor.value.triggerPoint)
        ? ["REJECT", "WARN", "SET_FIELD"]
        : ["REJECT", "WARN"],
);
const writableFields = computed(() => fieldCatalog.value.filter((field) => field.writable));
const selectedVersionReady = computed(() => schemeVersionId.value != null);

watch([schemeVersionId, status], () => void loadDefinitions());
watch(
    () => [editor.value.schemeVersionId, editor.value.scopeCategoryCode, editor.value.triggerPoint],
    () => {
        if (editorOpen.value) void loadFieldCatalog();
    },
);
onMounted(() => void loadDefinitions());

async function loadDefinitions() {
    if (!schemeVersionId.value) {
        definitions.value = [];
        return;
    }
    loading.value = true;
    try {
        const response = await listArchiveRuntimeDefinitions(schemeVersionId.value, status.value);
        definitions.value = triggerPoint.value
            ? response.items.filter((item) => item.triggerPoint === triggerPoint.value)
            : response.items;
        loadError.value = undefined;
    } catch (error) {
        loadError.value = requestErrorMessage(error, "运行时定义加载失败");
    } finally {
        loading.value = false;
    }
}

function openCreate() {
    if (!schemeVersionId.value) {
        ElMessage.warning("请先选择治理版本");
        return;
    }
    editingId.value = undefined;
    editor.value = { ...defaultEditor(), schemeVersionId: schemeVersionId.value };
    editorError.value = undefined;
    editorViolations.value = [];
    editorOpen.value = true;
}

function openEdit(value: unknown) {
    const row = value as ArchiveRuntimeDefinitionDto;
    editingId.value = row.id;
    editor.value = {
        schemeVersionId: row.schemeVersionId,
        definitionKind: row.definitionKind,
        definitionCode: row.definitionCode,
        definitionName: row.definitionName,
        triggerPoint: row.triggerPoint,
        scopeFondsCode: row.scopeFondsCode ?? "",
        scopeCategoryCode: row.scopeCategoryCode ?? "",
        priority: row.priority,
        conditionJson: JSON.stringify(row.conditionJson, null, 2),
        constraintAction: row.constraintAction ?? "REJECT",
        constraintMessage: row.constraintMessage ?? "",
        enabled: row.enabled,
        actions: row.actions.map((action) => ({
            actionType: action.actionType,
            message: String(action.actionParams.message ?? ""),
            field: String(action.actionParams.field ?? ""),
            value: JSON.stringify(action.actionParams.value ?? ""),
        })),
    };
    editorError.value = undefined;
    editorViolations.value = [];
    editorOpen.value = true;
}

async function loadFieldCatalog() {
    if (!editor.value.schemeVersionId) return;
    fieldLoading.value = true;
    try {
        const response = await getArchiveRuntimeFields({
            schemeVersionId: editor.value.schemeVersionId,
            categoryCode: trim(editor.value.scopeCategoryCode),
            triggerPoint: editor.value.triggerPoint,
        });
        fieldCatalog.value = response.fields;
    } catch (error) {
        fieldCatalog.value = [];
        editorError.value = requestErrorMessage(error, "字段目录加载失败");
    } finally {
        fieldLoading.value = false;
    }
}

function addAction() {
    editor.value.actions.push({ actionType: "WARN", message: "请复核", field: "", value: "" });
}

async function submitDefinition() {
    editorSubmitting.value = true;
    editorError.value = undefined;
    editorViolations.value = [];
    try {
        const payload = editorPayload();
        if (editingId.value) await updateArchiveRuntimeDefinition(editingId.value, payload);
        else await createArchiveRuntimeDefinition(payload);
        ElMessage.success(editingId.value ? "运行时定义已更新" : "运行时定义已创建");
        editorOpen.value = false;
        await loadDefinitions();
    } catch (error) {
        editorError.value = requestErrorMessage(error, "运行时定义保存失败");
        editorViolations.value =
            error instanceof HttpClientError
                ? error.fieldViolations.map((item) => ({
                      field: item.field ?? "definition",
                      message: item.message ?? "字段不合法",
                  }))
                : [];
    } finally {
        editorSubmitting.value = false;
    }
}

function editorPayload(): ArchiveRuntimeDefinitionRequest {
    const value = editor.value;
    if (!value.schemeVersionId || !value.definitionCode.trim() || !value.definitionName.trim()) {
        throw new Error("治理版本、编码和名称不能为空");
    }
    const actions =
        value.definitionKind === "RULE"
            ? value.actions.map((action, index) => ({
                  actionType: action.actionType,
                  actionOrder: index,
                  actionParams:
                      action.actionType === "SET_FIELD"
                          ? { field: action.field, value: parseJsonValue(action.value) }
                          : { message: action.message.trim() },
              }))
            : [];
    if (value.definitionKind === "RULE" && actions.length === 0) {
        throw new Error("运行时规则至少需要一个固定动作");
    }
    return {
        schemeVersionId: value.schemeVersionId,
        definitionKind: value.definitionKind,
        definitionCode: value.definitionCode.trim(),
        definitionName: value.definitionName.trim(),
        triggerPoint: value.triggerPoint,
        scopeFondsCode: trim(value.scopeFondsCode),
        scopeCategoryCode: trim(value.scopeCategoryCode),
        scopeArchiveLevel: value.triggerPoint.startsWith("VOLUME_") ? "VOLUME" : "ITEM",
        priority: value.priority,
        conditionJson: parseObject(value.conditionJson, "条件 JSON"),
        constraintAction:
            value.definitionKind === "CONSTRAINT" ? value.constraintAction : undefined,
        constraintMessage:
            value.definitionKind === "CONSTRAINT" ? trim(value.constraintMessage) : undefined,
        enabled: value.enabled,
        actions,
    };
}

async function publishDefinition(value: unknown) {
    const row = value as ArchiveRuntimeDefinitionDto;
    try {
        await publishArchiveRuntimeDefinition(row.id);
        ElMessage.success("定义已发布并锁定语义");
        await loadDefinitions();
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "定义发布失败"));
    }
}

async function changeEnabled(value: unknown, enabled: boolean) {
    const row = value as ArchiveRuntimeDefinitionDto;
    try {
        await (enabled
            ? enableArchiveRuntimeDefinition(row.id)
            : disableArchiveRuntimeDefinition(row.id));
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "启停失败"));
    } finally {
        await loadDefinitions();
    }
}

async function removeDefinition(value: unknown) {
    const row = value as ArchiveRuntimeDefinitionDto;
    try {
        await ElMessageBox.confirm(`删除草稿“${row.definitionName}”？`, "删除运行时定义", {
            type: "warning",
        });
        await deleteArchiveRuntimeDefinition(row.id);
        await loadDefinitions();
    } catch (error) {
        if (error !== "cancel" && error !== "close") {
            ElMessage.error(requestErrorMessage(error, "定义删除失败"));
        }
    }
}

function openSimulation() {
    if (!schemeVersionId.value) {
        ElMessage.warning("请先选择治理版本");
        return;
    }
    simulation.value = { ...defaultSimulation(), schemeVersionId: schemeVersionId.value };
    simulationResult.value = undefined;
    simulationError.value = undefined;
    simulationOpen.value = true;
}

async function runSimulation() {
    simulationSubmitting.value = true;
    simulationError.value = undefined;
    try {
        const value = simulation.value;
        simulationResult.value = await simulateArchiveRuntimeDefinitions({
            schemeVersionId: value.schemeVersionId!,
            triggerPoint: value.triggerPoint,
            fondsCode: trim(value.fondsCode),
            categoryCode: trim(value.categoryCode),
            archiveLevel: value.triggerPoint.startsWith("VOLUME_") ? "VOLUME" : "ITEM",
            objectTypeCode: "SIMULATION",
            candidateFacts: parseObject(value.candidateFacts, "候选事实 JSON"),
        });
    } catch (error) {
        simulationError.value = requestErrorMessage(error, "试运行失败");
    } finally {
        simulationSubmitting.value = false;
    }
}

function openSnapshot() {
    snapshotError.value = undefined;
    preflightResult.value = undefined;
    snapshotOpen.value = true;
}

async function exportSnapshot() {
    if (!schemeVersionId.value) return;
    snapshotSubmitting.value = true;
    try {
        const snapshot = await exportArchiveRuntimeSnapshot(schemeVersionId.value);
        snapshotJson.value = JSON.stringify(snapshot, null, 2);
        downloadJson(snapshot);
        ElMessage.success(`快照已生成，摘要 ${snapshot.sha256.slice(0, 12)}…`);
    } catch (error) {
        snapshotError.value = requestErrorMessage(error, "快照导出失败");
    } finally {
        snapshotSubmitting.value = false;
    }
}

async function readSnapshotFile(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (file.size > 1_048_576) {
        snapshotError.value = "快照不得超过 1 MiB";
        return;
    }
    snapshotJson.value = await file.text();
    preflightResult.value = undefined;
}

function snapshotPreflightRequest(): ArchiveRuntimeSnapshotPreflightRequest {
    return {
        snapshot: JSON.parse(snapshotJson.value) as ArchiveRuntimeSnapshot,
        targetSchemeCode: trim(snapshotTargetSchemeCode.value),
        categoryMappings: parseObject(categoryMappingsJson.value, "分类映射 JSON") as Record<
            string,
            string
        >,
        fieldMappings: parseObject(fieldMappingsJson.value, "字段映射 JSON") as Record<
            string,
            string
        >,
    };
}

async function preflightSnapshot() {
    snapshotSubmitting.value = true;
    snapshotError.value = undefined;
    try {
        preflightResult.value = await preflightArchiveRuntimeSnapshot(snapshotPreflightRequest());
    } catch (error) {
        preflightResult.value = undefined;
        snapshotError.value = requestErrorMessage(error, "快照预检失败");
    } finally {
        snapshotSubmitting.value = false;
    }
}

async function importSnapshot() {
    if (!preflightResult.value || !snapshotTargetVersionCode.value.trim()) {
        snapshotError.value = "请先通过预检并填写新的草稿版本编码";
        return;
    }
    snapshotSubmitting.value = true;
    try {
        const result = await importArchiveRuntimeSnapshot({
            preflight: snapshotPreflightRequest(),
            targetVersionCode: snapshotTargetVersionCode.value.trim(),
        });
        schemeVersionId.value = result.schemeVersionId;
        await loadDefinitions();
        ElMessage.success(`已创建草稿版本 ${result.versionCode}（ID ${result.schemeVersionId}）`);
        snapshotOpen.value = false;
    } catch (error) {
        snapshotError.value = requestErrorMessage(error, "快照导入失败，未产生部分配置");
    } finally {
        snapshotSubmitting.value = false;
    }
}

async function restoreSnapshot() {
    if (!preflightResult.value || !schemeVersionId.value) return;
    try {
        await ElMessageBox.confirm(
            `将以快照中的 ${preflightResult.value.definitionCount} 条定义全量替换当前草稿，失败会自动回滚。`,
            "确认恢复草稿",
            { type: "warning", confirmButtonText: "确认恢复" },
        );
        snapshotSubmitting.value = true;
        const result = await restoreArchiveRuntimeSnapshot(schemeVersionId.value, {
            preflight: snapshotPreflightRequest(),
        });
        ElMessage.success(
            `草稿已从 ${result.beforeDefinitionCount} 条恢复为 ${result.afterDefinitionCount} 条`,
        );
        snapshotOpen.value = false;
        await loadDefinitions();
    } catch (error) {
        if (error !== "cancel" && error !== "close") {
            snapshotError.value = requestErrorMessage(error, "恢复失败，原草稿保持不变");
        }
    } finally {
        snapshotSubmitting.value = false;
    }
}

function downloadJson(snapshot: ArchiveRuntimeSnapshot) {
    const href = URL.createObjectURL(
        new Blob([JSON.stringify(snapshot, null, 2)], { type: "application/json" }),
    );
    const anchor = document.createElement("a");
    anchor.href = href;
    anchor.download = snapshot.fileName;
    anchor.click();
    URL.revokeObjectURL(href);
}

async function copyFieldCode(fieldCode: string) {
    await window.navigator.clipboard?.writeText(fieldCode);
    ElMessage.success(`已复制 ${fieldCode}`);
}

function parseObject(value: string, label: string) {
    try {
        const parsed = JSON.parse(value || "{}");
        if (parsed == null || Array.isArray(parsed) || typeof parsed !== "object") {
            throw new Error();
        }
        return parsed as Record<string, unknown>;
    } catch {
        throw new Error(`${label} 必须是合法对象`);
    }
}

function parseJsonValue(value: string) {
    try {
        return JSON.parse(value);
    } catch {
        return value;
    }
}

function trim(value?: string) {
    return value?.trim() || undefined;
}

function defaultEditor() {
    return {
        schemeVersionId: undefined as number | undefined,
        definitionKind: "CONSTRAINT" as ArchiveRuntimeDefinitionKind,
        definitionCode: "",
        definitionName: "",
        triggerPoint: "ITEM_BEFORE_CREATE" as ArchiveRuntimeTriggerPoint,
        scopeFondsCode: "",
        scopeCategoryCode: "",
        priority: 0,
        conditionJson: JSON.stringify({ field: "item.archiveNo", operator: "IS_EMPTY" }, null, 2),
        constraintAction: "REJECT" as "REJECT" | "WARN",
        constraintMessage: "档号不能为空",
        enabled: true,
        actions: [] as Array<{
            actionType: ArchiveRuntimeActionType;
            message: string;
            field: string;
            value: string;
        }>,
    };
}

function defaultSimulation() {
    return {
        schemeVersionId: undefined as number | undefined,
        triggerPoint: "ITEM_BEFORE_CREATE" as ArchiveRuntimeTriggerPoint,
        fondsCode: "",
        categoryCode: "",
        candidateFacts: JSON.stringify(
            {
                "item.archiveNo": "A-001",
                "item.archiveYear": 2026,
                "context.userId": 1,
            },
            null,
            2,
        ),
    };
}
</script>

<template>
    <section class="am-page runtime-page">
        <div class="am-page__header runtime-header">
            <div>
                <p class="runtime-eyebrow">RUNTIME POLICY</p>
                <h1>运行时约束与规则</h1>
                <p class="runtime-subtitle">用户定义条件，系统只执行固定触发点与固定动作。</p>
            </div>
            <div class="am-page__actions">
                <el-button :disabled="!selectedVersionReady" @click="openSnapshot"
                    >迁移与恢复</el-button
                >
                <el-button :disabled="!selectedVersionReady" @click="openSimulation"
                    >试运行</el-button
                >
                <el-button type="primary" @click="openCreate">新建定义</el-button>
            </div>
        </div>

        <el-card class="runtime-filter" shadow="never">
            <div class="runtime-filter__grid">
                <label>
                    <span>治理版本 ID</span>
                    <el-input-number v-model="schemeVersionId" :min="1" controls-position="right" />
                </label>
                <label>
                    <span>状态</span>
                    <el-select v-model="status" clearable placeholder="全部状态">
                        <el-option label="草稿" value="DRAFT" />
                        <el-option label="已发布" value="PUBLISHED" />
                    </el-select>
                </label>
                <label>
                    <span>触发点</span>
                    <el-select
                        v-model="triggerPoint"
                        clearable
                        placeholder="全部触发点"
                        @change="loadDefinitions"
                    >
                        <el-option
                            v-for="item in triggerPoints"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                        />
                    </el-select>
                </label>
                <el-button :loading="loading" @click="loadDefinitions">刷新</el-button>
            </div>
        </el-card>

        <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />
        <el-card class="runtime-list" shadow="never">
            <el-empty
                v-if="!loading && definitions.length === 0"
                description="当前版本还没有运行时定义"
            >
                <el-button type="primary" @click="openCreate">创建第一条约束</el-button>
            </el-empty>
            <el-table v-else v-loading="loading" :data="definitions" row-key="id" size="small">
                <el-table-column prop="priority" label="#" width="60" />
                <el-table-column label="定义" min-width="220">
                    <template #default="{ row }">
                        <div class="definition-cell">
                            <strong>{{ row.definitionName }}</strong>
                            <code>{{ row.definitionCode }}</code>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column label="类别" width="100">
                    <template #default="{ row }"
                        ><el-tag effect="plain">{{
                            row.definitionKind === "CONSTRAINT" ? "约束" : "规则"
                        }}</el-tag></template
                    >
                </el-table-column>
                <el-table-column label="触发点" min-width="170">
                    <template #default="{ row }">{{ triggerLabels[row.triggerPoint] }}</template>
                </el-table-column>
                <el-table-column prop="scopeCategoryCode" label="分类范围" min-width="130">
                    <template #default="{ row }">{{
                        row.scopeCategoryCode || "全部分类"
                    }}</template>
                </el-table-column>
                <el-table-column label="动作" min-width="150">
                    <template #default="{ row }">
                        <span v-if="row.definitionKind === 'CONSTRAINT'">{{
                            row.constraintAction
                        }}</span>
                        <el-tag
                            v-for="action in row.actions"
                            v-else
                            :key="action.id"
                            class="action-tag"
                            size="small"
                            effect="plain"
                            >{{ action.actionType }}</el-tag
                        >
                    </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                    <template #default="{ row }"
                        ><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{
                            row.status === "PUBLISHED" ? "已发布" : "草稿"
                        }}</el-tag></template
                    >
                </el-table-column>
                <el-table-column label="启用" width="78">
                    <template #default="{ row }"
                        ><el-switch
                            :model-value="row.enabled"
                            :disabled="row.status !== 'PUBLISHED'"
                            @change="changeEnabled(row, Boolean($event))"
                    /></template>
                </el-table-column>
                <el-table-column label="操作" width="190" fixed="right">
                    <template #default="{ row }">
                        <el-button v-if="row.status === 'DRAFT'" link @click="openEdit(row)"
                            >编辑</el-button
                        >
                        <el-button
                            v-if="row.status === 'DRAFT'"
                            link
                            type="primary"
                            @click="publishDefinition(row)"
                            >发布</el-button
                        >
                        <el-button
                            v-if="row.status === 'DRAFT'"
                            link
                            type="danger"
                            @click="removeDefinition(row)"
                            >删除</el-button
                        >
                        <span v-else class="immutable-label">语义已锁定</span>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <el-dialog
            v-model="editorOpen"
            :title="editingId ? '编辑运行时定义' : '新建运行时定义'"
            width="min(980px, 94vw)"
            destroy-on-close
        >
            <el-alert
                v-if="editorError"
                :title="editorError"
                type="error"
                :closable="false"
                show-icon
            >
                <ul v-if="editorViolations.length" class="violation-list">
                    <li v-for="item in editorViolations" :key="`${item.field}-${item.message}`">
                        <code>{{ item.field }}</code> {{ item.message }}
                    </li>
                </ul>
            </el-alert>
            <div class="editor-grid">
                <el-form :model="editor" label-position="top" class="editor-form">
                    <div class="editor-form__row">
                        <el-form-item label="定义类型"
                            ><el-segmented
                                v-model="editor.definitionKind"
                                :options="[
                                    { label: '约束', value: 'CONSTRAINT' },
                                    { label: '规则', value: 'RULE' },
                                ]"
                        /></el-form-item>
                        <el-form-item label="优先级"
                            ><el-input-number v-model="editor.priority" controls-position="right"
                        /></el-form-item>
                    </div>
                    <div class="editor-form__row">
                        <el-form-item label="稳定编码" required
                            ><el-input
                                v-model="editor.definitionCode"
                                placeholder="archive-no-required"
                        /></el-form-item>
                        <el-form-item label="名称" required
                            ><el-input v-model="editor.definitionName" placeholder="档号必填"
                        /></el-form-item>
                    </div>
                    <div class="editor-form__row">
                        <el-form-item label="固定触发点"
                            ><el-select v-model="editor.triggerPoint"
                                ><el-option
                                    v-for="item in triggerPoints"
                                    :key="item.value"
                                    :label="item.label"
                                    :value="item.value" /></el-select
                        ></el-form-item>
                        <el-form-item label="分类编码"
                            ><el-input
                                v-model="editor.scopeCategoryCode"
                                placeholder="为空表示全部分类"
                        /></el-form-item>
                        <el-form-item label="全宗编码"
                            ><el-input v-model="editor.scopeFondsCode" placeholder="可选"
                        /></el-form-item>
                    </div>
                    <el-form-item label="结构化条件 JSON" required>
                        <el-input
                            v-model="editor.conditionJson"
                            type="textarea"
                            :rows="9"
                            class="code-input"
                        />
                        <div class="field-hint">
                            只允许 all / any / not / 字段比较节点，不接受 SQL、脚本或表达式语言。
                        </div>
                    </el-form-item>
                    <template v-if="editor.definitionKind === 'CONSTRAINT'">
                        <div class="editor-form__row">
                            <el-form-item label="断言失败处理"
                                ><el-select v-model="editor.constraintAction"
                                    ><el-option label="阻断 REJECT" value="REJECT" /><el-option
                                        label="警告 WARN"
                                        value="WARN" /></el-select
                            ></el-form-item>
                            <el-form-item label="用户消息"
                                ><el-input v-model="editor.constraintMessage"
                            /></el-form-item>
                        </div>
                    </template>
                    <template v-else>
                        <div class="action-header">
                            <strong>固定动作</strong
                            ><el-button size="small" @click="addAction">添加动作</el-button>
                        </div>
                        <div
                            v-for="(action, index) in editor.actions"
                            :key="index"
                            class="action-row"
                        >
                            <span class="action-index">{{ index + 1 }}</span>
                            <el-select v-model="action.actionType" style="width: 150px"
                                ><el-option
                                    v-for="value in availableActions"
                                    :key="value"
                                    :label="value"
                                    :value="value"
                            /></el-select>
                            <template v-if="action.actionType === 'SET_FIELD'">
                                <el-select
                                    v-model="action.field"
                                    filterable
                                    placeholder="可写字段"
                                    class="action-grow"
                                    ><el-option
                                        v-for="field in writableFields"
                                        :key="field.fieldCode"
                                        :label="`${field.fieldName} · ${field.fieldCode}`"
                                        :value="field.fieldCode"
                                /></el-select>
                                <el-input
                                    v-model="action.value"
                                    placeholder='JSON 值，如 2026 或 "DRAFT"'
                                    class="action-grow"
                                />
                            </template>
                            <el-input
                                v-else
                                v-model="action.message"
                                placeholder="用户可见消息"
                                class="action-grow"
                            />
                            <el-button link type="danger" @click="editor.actions.splice(index, 1)"
                                >移除</el-button
                            >
                        </div>
                    </template>
                    <el-form-item label="创建后启用"
                        ><el-switch v-model="editor.enabled"
                    /></el-form-item>
                </el-form>
                <aside v-loading="fieldLoading" class="field-catalog">
                    <div class="field-catalog__header">
                        <strong>真实字段目录</strong><span>{{ fieldCatalog.length }} 个字段</span>
                    </div>
                    <el-empty
                        v-if="!fieldCatalog.length"
                        description="填写有效分类后加载字段"
                        :image-size="64"
                    />
                    <button
                        v-for="field in fieldCatalog"
                        v-else
                        :key="field.fieldCode"
                        type="button"
                        class="field-item"
                        @click="copyFieldCode(field.fieldCode)"
                    >
                        <span
                            ><strong>{{ field.fieldName }}</strong
                            ><code>{{ field.fieldCode }}</code></span
                        >
                        <span class="field-meta"
                            >{{ field.dataType }} · {{ field.writable ? "可写" : "只读" }}</span
                        >
                    </button>
                </aside>
            </div>
            <template #footer
                ><el-button @click="editorOpen = false">取消</el-button
                ><el-button type="primary" :loading="editorSubmitting" @click="submitDefinition"
                    >保存草稿</el-button
                ></template
            >
        </el-dialog>

        <el-dialog
            v-model="simulationOpen"
            title="无副作用试运行"
            width="min(900px, 94vw)"
            destroy-on-close
        >
            <el-alert
                title="试运行复用真实执行核心，但不会写主数据、审计或决策追踪。"
                type="info"
                :closable="false"
                show-icon
            />
            <el-form :model="simulation" label-position="top" class="simulation-form">
                <div class="editor-form__row">
                    <el-form-item label="触发点"
                        ><el-select v-model="simulation.triggerPoint"
                            ><el-option
                                v-for="item in triggerPoints"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value" /></el-select></el-form-item
                    ><el-form-item label="全宗编码"
                        ><el-input v-model="simulation.fondsCode" /></el-form-item
                    ><el-form-item label="分类编码"
                        ><el-input v-model="simulation.categoryCode"
                    /></el-form-item>
                </div>
                <el-form-item label="候选事实 JSON"
                    ><el-input
                        v-model="simulation.candidateFacts"
                        type="textarea"
                        :rows="8"
                        class="code-input"
                /></el-form-item>
            </el-form>
            <el-alert
                v-if="simulationError"
                :title="simulationError"
                type="error"
                :closable="false"
            />
            <template v-if="simulationResult">
                <div class="simulation-summary">
                    <el-tag
                        :type="
                            simulationResult.blocking
                                ? 'danger'
                                : simulationResult.warnings.length
                                  ? 'warning'
                                  : 'success'
                        "
                        >{{
                            simulationResult.blocking
                                ? "将阻断"
                                : simulationResult.warnings.length
                                  ? "放行但有警告"
                                  : "允许执行"
                        }}</el-tag
                    ><span
                        >候选字段变化
                        {{ Object.keys(simulationResult.assignments).length }} 项</span
                    >
                </div>
                <el-table :data="simulationResult.decisions" size="small"
                    ><el-table-column prop="definitionCode" label="定义" /><el-table-column
                        prop="definitionKind"
                        label="类型"
                        width="100" /><el-table-column label="命中" width="80"
                        ><template #default="{ row }">{{
                            row.matched ? "是" : "否"
                        }}</template></el-table-column
                    ><el-table-column label="结果" width="100"
                        ><template #default="{ row }"
                            ><el-tag
                                :type="
                                    row.blocking
                                        ? 'danger'
                                        : row.severity === 'WARNING'
                                          ? 'warning'
                                          : 'info'
                                "
                                >{{ row.blocking ? "阻断" : row.severity }}</el-tag
                            ></template
                        ></el-table-column
                    ><el-table-column prop="message" label="消息"
                /></el-table>
                <el-collapse
                    ><el-collapse-item title="最终候选事实">
                        <pre>{{ JSON.stringify(simulationResult.candidateFacts, null, 2) }}</pre>
                    </el-collapse-item></el-collapse
                >
            </template>
            <template #footer
                ><el-button @click="simulationOpen = false">关闭</el-button
                ><el-button type="primary" :loading="simulationSubmitting" @click="runSimulation"
                    >开始试运行</el-button
                ></template
            >
        </el-dialog>

        <el-dialog
            v-model="snapshotOpen"
            title="运行时配置迁移与恢复"
            width="min(900px, 94vw)"
            destroy-on-close
        >
            <div class="snapshot-toolbar">
                <el-button
                    :disabled="!schemeVersionId"
                    :loading="snapshotSubmitting"
                    @click="exportSnapshot"
                    >导出当前版本</el-button
                ><label class="file-button"
                    ><span>选择快照文件</span
                    ><input type="file" accept="application/json,.json" @change="readSnapshotFile"
                /></label>
            </div>
            <el-form label-position="top">
                <el-form-item label="快照 JSON"
                    ><el-input
                        v-model="snapshotJson"
                        type="textarea"
                        :rows="7"
                        class="code-input"
                        placeholder="导出、选择文件或粘贴快照"
                /></el-form-item>
                <div class="editor-form__row">
                    <el-form-item label="目标治理方案编码"
                        ><el-input
                            v-model="snapshotTargetSchemeCode"
                            placeholder="为空沿用快照方案编码" /></el-form-item
                    ><el-form-item label="新草稿版本编码"
                        ><el-input
                            v-model="snapshotTargetVersionCode"
                            placeholder="跨环境导入时必填"
                    /></el-form-item>
                </div>
                <div class="editor-form__row">
                    <el-form-item label="分类映射 JSON"
                        ><el-input
                            v-model="categoryMappingsJson"
                            type="textarea"
                            :rows="3"
                            class="code-input" /></el-form-item
                    ><el-form-item label="字段映射 JSON"
                        ><el-input
                            v-model="fieldMappingsJson"
                            type="textarea"
                            :rows="3"
                            class="code-input"
                    /></el-form-item>
                </div>
            </el-form>
            <el-alert
                v-if="snapshotError"
                :title="snapshotError"
                type="error"
                :closable="false"
                show-icon
            />
            <el-card v-if="preflightResult" class="preflight-result" shadow="never"
                ><div class="preflight-result__summary">
                    <el-tag type="success">预检通过</el-tag
                    ><strong>{{ preflightResult.definitionCount }} 条定义</strong
                    ><span>{{ preflightResult.fieldMappings.length }} 个字段引用</span
                    ><code>{{ preflightResult.sha256 }}</code>
                </div>
                <el-table :data="preflightResult.fieldMappings" max-height="220" size="small"
                    ><el-table-column prop="definitionCode" label="定义" /><el-table-column
                        prop="sourceFieldCode"
                        label="源字段" /><el-table-column
                        prop="targetFieldCode"
                        label="目标字段" /><el-table-column
                        prop="dataType"
                        label="类型"
                        width="100" /></el-table
            ></el-card>
            <template #footer
                ><el-button @click="snapshotOpen = false">关闭</el-button
                ><el-button :loading="snapshotSubmitting" @click="preflightSnapshot"
                    >完整预检</el-button
                ><el-button :disabled="!preflightResult" @click="restoreSnapshot"
                    >恢复当前草稿</el-button
                ><el-button
                    type="primary"
                    :disabled="!preflightResult"
                    :loading="snapshotSubmitting"
                    @click="importSnapshot"
                    >导入为新草稿</el-button
                ></template
            >
        </el-dialog>
    </section>
</template>

<style scoped>
.runtime-page {
    --runtime-ink: #18222d;
    --runtime-muted: #667281;
    --runtime-line: #dfe5e8;
}
.runtime-header {
    align-items: flex-end;
}
.runtime-eyebrow {
    margin: 0 0 6px;
    color: var(--el-color-primary);
    font:
        700 11px/1.2 ui-monospace,
        monospace;
    letter-spacing: 0.16em;
}
.runtime-subtitle {
    margin: 6px 0 0;
    color: var(--runtime-muted);
    font-size: 13px;
}
.runtime-filter {
    margin-bottom: 14px;
}
.runtime-filter__grid {
    display: grid;
    grid-template-columns: minmax(180px, 240px) minmax(140px, 180px) minmax(220px, 1fr) auto;
    gap: 14px;
    align-items: end;
}
.runtime-filter__grid label {
    display: grid;
    gap: 7px;
    color: var(--runtime-muted);
    font-size: 12px;
}
.runtime-list :deep(.el-card__body) {
    padding: 0;
}
.definition-cell {
    display: grid;
    gap: 3px;
}
.definition-cell strong {
    color: var(--runtime-ink);
}
.definition-cell code,
.field-item code {
    color: var(--runtime-muted);
    font-size: 11px;
}
.action-tag + .action-tag {
    margin-left: 4px;
}
.immutable-label {
    color: var(--runtime-muted);
    font-size: 12px;
}
.editor-grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 280px;
    gap: 20px;
    margin-top: 16px;
}
.editor-form__row {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
}
.editor-form__row:has(> :nth-child(2):last-child) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
}
.code-input :deep(textarea) {
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 12px;
    line-height: 1.55;
}
.field-hint {
    margin-top: 6px;
    color: var(--runtime-muted);
    font-size: 12px;
}
.field-catalog {
    min-height: 360px;
    overflow: hidden;
    border: 1px solid var(--runtime-line);
    border-radius: 8px;
    background: #f8fafb;
}
.field-catalog__header {
    display: flex;
    justify-content: space-between;
    padding: 12px 14px;
    border-bottom: 1px solid var(--runtime-line);
}
.field-catalog__header span {
    color: var(--runtime-muted);
    font-size: 12px;
}
.field-item {
    display: flex;
    width: 100%;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 14px;
    border: 0;
    border-bottom: 1px solid var(--runtime-line);
    background: transparent;
    color: inherit;
    text-align: left;
    cursor: pointer;
}
.field-item:hover {
    background: #fff;
}
.field-item > span:first-child {
    display: grid;
    gap: 2px;
}
.field-meta {
    flex: none;
    color: var(--runtime-muted);
    font-size: 11px;
}
.action-header,
.snapshot-toolbar,
.simulation-summary,
.preflight-result__summary {
    display: flex;
    align-items: center;
    gap: 12px;
}
.action-header {
    justify-content: space-between;
    margin-bottom: 10px;
}
.action-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
}
.action-index {
    width: 22px;
    color: var(--runtime-muted);
    text-align: center;
}
.action-grow {
    flex: 1;
}
.violation-list {
    margin: 8px 0 0;
    padding-left: 18px;
}
.simulation-form {
    margin-top: 14px;
}
.simulation-summary {
    margin: 14px 0 10px;
}
pre {
    max-height: 260px;
    overflow: auto;
    margin: 0;
    padding: 12px;
    background: #f5f7f8;
    font-size: 12px;
}
.snapshot-toolbar {
    margin-bottom: 14px;
}
.file-button {
    display: inline-flex;
    align-items: center;
    height: 32px;
    padding: 0 15px;
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
    cursor: pointer;
}
.file-button input {
    display: none;
}
.preflight-result {
    margin-top: 12px;
}
.preflight-result__summary {
    flex-wrap: wrap;
    margin-bottom: 10px;
}
.preflight-result__summary code {
    margin-left: auto;
    color: var(--runtime-muted);
    font-size: 10px;
}
@media (max-width: 900px) {
    .runtime-filter__grid,
    .editor-grid {
        grid-template-columns: 1fr;
    }
    .field-catalog {
        min-height: 220px;
    }
}
@media (max-width: 640px) {
    .runtime-header {
        align-items: flex-start;
    }
    .editor-form__row {
        grid-template-columns: 1fr;
    }
    .action-row {
        align-items: stretch;
        flex-direction: column;
    }
}
</style>
