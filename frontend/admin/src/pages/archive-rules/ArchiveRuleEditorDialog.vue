<script setup lang="ts">
import { HttpClientError } from "@archive-management/frontend-core/api";
import { ElMessage } from "element-plus";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import {
    createArchiveRuntimeDefinition,
    getArchiveRuntimeFields,
    updateArchiveRuntimeDefinition,
} from "@/shared/api/archive-rules";
import { requestErrorMessage } from "@/shared/requestError";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import type {
    ArchiveRuntimeActionType,
    ArchiveRuntimeDefinitionDto,
    ArchiveRuntimeDefinitionRequest,
    ArchiveRuntimeFieldDto,
} from "@/shared/types/archive-rules";
import {
    assignmentTriggers,
    defaultEditor,
    parseJsonValue,
    parseObject,
    triggerPoints,
    trim,
} from "./archiveRuleForm";

const props = defineProps<{ definition?: ArchiveRuntimeDefinitionDto }>();
const editorOpen = defineModel<boolean>({ required: true });
const emit = defineEmits<{ saved: [] }>();
const editingId = computed(() => props.definition?.id);
const editorSubmitting = ref(false);
const editorError = ref<string>();
const editorViolations = ref<Array<{ field: string; message: string }>>([]);
const fieldLoading = ref(false);
const fieldCatalogError = ref<string>();
const fieldCatalog = ref<ArchiveRuntimeFieldDto[]>([]);
const editor = ref(defaultEditor());
let fieldRequestVersion = 0;
let disposed = false;

const availableActions = computed<ArchiveRuntimeActionType[]>(() =>
    assignmentTriggers.has(editor.value.triggerPoint)
        ? ["REJECT", "WARN", "SET_FIELD"]
        : ["REJECT", "WARN"],
);
const writableFields = computed(() => fieldCatalog.value.filter((field) => field.writable));

watch(editorOpen, (open) => {
    invalidateFieldRequest();
    fieldCatalog.value = [];
    fieldCatalogError.value = undefined;
    if (!open) return;
    if (props.definition) openEdit(props.definition);
    else openCreate();
});
watch(
    () => [editorOpen.value, editor.value.scopeCategoryCode, editor.value.triggerPoint],
    () => {
        if (editorOpen.value) void loadFieldCatalog();
    },
);
onBeforeUnmount(() => {
    disposed = true;
    invalidateFieldRequest();
});
function invalidateFieldRequest() {
    fieldRequestVersion += 1;
    fieldLoading.value = false;
}
function openCreate() {
    editor.value = defaultEditor();
    editorError.value = undefined;
    editorViolations.value = [];
}

function openEdit(value: unknown) {
    const row = value as ArchiveRuntimeDefinitionDto;
    editor.value = {
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
}

async function loadFieldCatalog(preserveError = false) {
    if (!editorOpen.value || disposed) return;
    const version = ++fieldRequestVersion;
    const categoryCode = trim(editor.value.scopeCategoryCode);
    const triggerPoint = editor.value.triggerPoint;
    const isCurrent = () =>
        !disposed &&
        editorOpen.value &&
        version === fieldRequestVersion &&
        categoryCode === trim(editor.value.scopeCategoryCode) &&
        triggerPoint === editor.value.triggerPoint;
    fieldLoading.value = true;
    fieldCatalog.value = [];
    if (!preserveError) fieldCatalogError.value = undefined;
    try {
        const response = await getArchiveRuntimeFields({
            categoryCode,
            triggerPoint,
        });
        if (!isCurrent()) return;
        fieldCatalog.value = response.fields;
        fieldCatalogError.value = undefined;
    } catch (error) {
        if (isCurrent()) fieldCatalogError.value = requestErrorMessage(error, "字段目录加载失败");
    } finally {
        if (isCurrent()) fieldLoading.value = false;
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
        emit("saved");
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
    if (!value.definitionCode.trim() || !value.definitionName.trim()) {
        throw new Error("编码和名称不能为空");
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

async function copyFieldCode(fieldCode: string) {
    await window.navigator.clipboard?.writeText(fieldCode);
    ElMessage.success(`已复制 ${fieldCode}`);
}
</script>

<template>
    <el-dialog
        v-model="editorOpen"
        :title="editingId ? '编辑运行时定义' : '新建运行时定义'"
        width="min(980px, 94vw)"
        destroy-on-close
        @close="invalidateFieldRequest"
    >
        <el-alert v-if="editorError" :title="editorError" type="error" :closable="false" show-icon>
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
                    <div v-for="(action, index) in editor.actions" :key="index" class="action-row">
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
                <RequestErrorState
                    v-if="fieldCatalogError"
                    :message="fieldCatalogError"
                    :retrying="fieldLoading"
                    retry-label="重试字段目录"
                    @retry="void loadFieldCatalog(true)"
                />
                <el-empty
                    v-else-if="!fieldLoading && !fieldCatalog.length"
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
</template>

<style scoped>
.field-item code {
    color: var(--runtime-muted);
    font-size: 11px;
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
.action-header {
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
@media (max-width: 900px) {
    .editor-grid {
        grid-template-columns: 1fr;
    }
    .field-catalog {
        min-height: 220px;
    }
}
@media (max-width: 640px) {
    .editor-form__row {
        grid-template-columns: 1fr;
    }
    .action-row {
        align-items: stretch;
        flex-direction: column;
    }
}
</style>
