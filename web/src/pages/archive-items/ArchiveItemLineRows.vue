<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";

import { errorMessage, HttpClientError } from "@archive-management/frontend-core/api";

import {
    createArchiveItemLineRow,
    deleteArchiveItemLineRow,
    listArchiveItemLineRows,
    listArchiveItemLineTables,
    patchArchiveItemLineRow,
} from "@/shared/api/archive-line-tables";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import type {
    ArchiveItemLineRowResponse,
    ArchiveItemLineFieldDefinitionResponse,
    ArchiveItemLineTableDefinitionResponse,
    PatchArchiveItemLineRowRequest,
} from "@/shared/types/archive-line-tables";
import type { CursorPageResponse } from "@/shared/types/pagination";

const props = defineProps<{
    archiveItemId?: number;
    readonly: boolean;
}>();

interface LineTableState {
    page?: CursorPageResponse<ArchiveItemLineRowResponse>;
    cursor?: string;
    limit: number;
    loading: boolean;
    error?: string;
    requestVersion: number;
}

type FormValue = string | number | null;

const tables = ref<ArchiveItemLineTableDefinitionResponse[]>([]);
const tablesLoading = ref(false);
const tablesError = ref<string>();
const activeTableId = ref<number>();
const states = reactive<Record<number, LineTableState>>({});
const editingRow = ref<ArchiveItemLineRowResponse | "create">();
const lineOrder = ref(0);
const formValues = reactive<Record<string, FormValue>>({});
const formError = ref<string>();
const fieldErrors = reactive<Record<string, string>>({});
const saving = ref(false);
const deletingRowId = ref<number>();
let contextVersion = 0;
let tableRequestVersion = 0;
let commandRequestVersion = 0;
let disposed = false;

const activeTable = computed(() => tables.value.find((table) => table.id === activeTableId.value));
const activeState = computed(() =>
    activeTableId.value === undefined ? undefined : states[activeTableId.value],
);
const commandBusy = computed(() => saving.value || deletingRowId.value !== undefined);

watch(
    () => props.archiveItemId,
    (archiveItemId) => {
        resetContext();
        if (archiveItemId) void loadTables(archiveItemId);
    },
    { immediate: true },
);

watch(
    () => props.readonly,
    (readonly) => {
        if (readonly) cancelEdit();
    },
);

onBeforeUnmount(() => {
    disposed = true;
    resetContext();
});

async function loadTables(archiveItemId = props.archiveItemId) {
    if (!archiveItemId) return;
    const context = contextVersion;
    const version = ++tableRequestVersion;
    tablesLoading.value = true;
    tablesError.value = undefined;
    try {
        const response = await listArchiveItemLineTables(archiveItemId);
        const definitions = response.items;
        if (!isCurrent(context, archiveItemId) || version !== tableRequestVersion) return;
        tables.value = definitions;
        for (const table of definitions) {
            states[table.id] = createState();
        }
        activeTableId.value = definitions[0]?.id;
        if (activeTableId.value !== undefined) await loadRows(activeTableId.value, undefined);
    } catch (error) {
        if (isCurrent(context, archiveItemId) && version === tableRequestVersion)
            tablesError.value = errorMessage(error, "加载明细表定义失败");
    } finally {
        if (version === tableRequestVersion) tablesLoading.value = false;
    }
}

function createState(): LineTableState {
    return { limit: 100, loading: false, requestVersion: 0 };
}

async function loadRows(tableId: number, cursor = states[tableId]?.cursor) {
    const archiveItemId = props.archiveItemId;
    const state = states[tableId];
    if (!archiveItemId || !state) return;
    const context = contextVersion;
    const version = ++state.requestVersion;
    state.cursor = cursor;
    state.loading = true;
    state.error = undefined;
    try {
        const response = await listArchiveItemLineRows(archiveItemId, tableId, {
            limit: state.limit,
            cursor,
        });
        if (isCurrent(context, archiveItemId) && version === state.requestVersion)
            state.page = response;
    } catch (error) {
        if (isCurrent(context, archiveItemId) && version === state.requestVersion)
            state.error = errorMessage(error, "加载明细行失败");
    } finally {
        if (version === state.requestVersion) state.loading = false;
    }
}

function changeTable(name: string | number) {
    if (commandBusy.value) return;
    const tableId = Number(name);
    if (!Number.isSafeInteger(tableId)) return;
    activeTableId.value = tableId;
    cancelEdit();
    if (!states[tableId]?.page && !states[tableId]?.loading) void loadRows(tableId, undefined);
}

function changeLimit(limit: number) {
    const state = activeState.value;
    const tableId = activeTableId.value;
    if (!state || tableId === undefined) return;
    state.limit = limit;
    state.cursor = undefined;
    void loadRows(tableId, undefined);
}

function startCreate() {
    if (props.readonly || commandBusy.value || !activeTable.value) return;
    editingRow.value = "create";
    lineOrder.value = 0;
    resetFormValues(activeTable.value.fields, {});
}

function startEdit(value: unknown) {
    if (props.readonly || commandBusy.value || !activeTable.value) return;
    const row = value as ArchiveItemLineRowResponse;
    editingRow.value = row;
    lineOrder.value = row.lineOrder;
    resetFormValues(activeTable.value.fields, row.values);
}

function resetFormValues(
    fields: ArchiveItemLineFieldDefinitionResponse[],
    values: Record<string, unknown | null>,
) {
    for (const key of Object.keys(formValues)) delete formValues[key];
    for (const field of fields) formValues[field.fieldCode] = formValue(values[field.fieldCode]);
    formError.value = undefined;
    clearFieldErrors();
}

function cancelEdit() {
    if (editingRow.value || saving.value) commandRequestVersion += 1;
    saving.value = false;
    editingRow.value = undefined;
    formError.value = undefined;
    clearFieldErrors();
}

async function saveRow() {
    const archiveItemId = props.archiveItemId;
    const table = activeTable.value;
    const state = activeState.value;
    const editing = editingRow.value;
    if (
        props.readonly ||
        !archiveItemId ||
        !table ||
        !state ||
        !editing ||
        commandBusy.value ||
        lineOrder.value < 0
    )
        return;
    const context = contextVersion;
    const version = ++commandRequestVersion;
    saving.value = true;
    formError.value = undefined;
    clearFieldErrors();
    try {
        if (editing === "create") {
            await createArchiveItemLineRow(archiveItemId, table.id, {
                lineOrder: lineOrder.value,
                values: requestValues(table.fields),
            });
        } else {
            const payload = patchPayload(table.fields, editing);
            if (!payload.values && payload.lineOrder === undefined) {
                editingRow.value = undefined;
                return;
            }
            await patchArchiveItemLineRow(archiveItemId, table.id, editing.id, payload);
        }
        if (!isCurrent(context, archiveItemId) || version !== commandRequestVersion) return;
        ElMessage.success(editing === "create" ? "明细行已新增" : "明细行已更新");
        editingRow.value = undefined;
        await loadRows(table.id, state.cursor);
    } catch (error) {
        if (isCurrent(context, archiveItemId) && version === commandRequestVersion) {
            applyFieldErrors(error, table.fields);
            formError.value = errorMessage(error, "保存明细行失败");
        }
    } finally {
        if (version === commandRequestVersion) saving.value = false;
    }
}

function clearFieldErrors() {
    for (const key of Object.keys(fieldErrors)) delete fieldErrors[key];
}

function applyFieldErrors(error: unknown, fields: ArchiveItemLineFieldDefinitionResponse[]) {
    if (!(error instanceof HttpClientError)) return;
    const fieldCodes = new Set(fields.map((field) => field.fieldCode));
    for (const violation of error.fieldViolations) {
        if (!violation.field || !violation.message) continue;
        if (violation.field === "lineOrder") fieldErrors.lineOrder = violation.message;
        if (violation.field.startsWith("values.")) {
            const fieldCode = violation.field.slice("values.".length);
            if (fieldCodes.has(fieldCode)) fieldErrors[fieldCode] = violation.message;
        }
    }
}

function requestValues(fields: ArchiveItemLineFieldDefinitionResponse[]) {
    return Object.fromEntries(
        fields.map((field) => [field.fieldCode, requestValue(field, formValues[field.fieldCode])]),
    );
}

function patchPayload(
    fields: ArchiveItemLineFieldDefinitionResponse[],
    row: ArchiveItemLineRowResponse,
): PatchArchiveItemLineRowRequest {
    const changedValues: Record<string, unknown | null> = {};
    for (const field of fields) {
        const current = requestValue(field, formValues[field.fieldCode]);
        const original = requestValue(field, formValue(row.values[field.fieldCode]));
        if (!Object.is(current, original)) changedValues[field.fieldCode] = current;
    }
    return {
        ...(lineOrder.value !== row.lineOrder ? { lineOrder: lineOrder.value } : {}),
        ...(Object.keys(changedValues).length ? { values: changedValues } : {}),
    };
}

function requestValue(
    field: ArchiveItemLineFieldDefinitionResponse,
    value: FormValue,
): unknown | null {
    if (value === undefined || value === null || value === "") return null;
    if (field.fieldType === "INTEGER") return Number(value);
    return value;
}

async function removeRow(value: unknown) {
    const row = value as ArchiveItemLineRowResponse;
    const archiveItemId = props.archiveItemId;
    const tableId = activeTableId.value;
    const state = activeState.value;
    if (props.readonly || !archiveItemId || !tableId || !state || commandBusy.value) return;
    const context = contextVersion;
    try {
        await ElMessageBox.confirm("确认删除该明细行吗？", "删除明细行", {
            type: "warning",
            confirmButtonText: "删除",
            cancelButtonText: "取消",
        });
    } catch {
        return;
    }
    if (
        props.readonly ||
        commandBusy.value ||
        !isCurrent(context, archiveItemId) ||
        activeTableId.value !== tableId
    )
        return;
    const version = ++commandRequestVersion;
    deletingRowId.value = row.id;
    try {
        await deleteArchiveItemLineRow(archiveItemId, tableId, row.id);
        if (!isCurrent(context, archiveItemId) || version !== commandRequestVersion) return;
        ElMessage.success("明细行已删除");
        await loadRows(tableId, state.cursor);
    } catch (error) {
        if (isCurrent(context, archiveItemId) && version === commandRequestVersion)
            ElMessage.error(errorMessage(error, "删除明细行失败"));
    } finally {
        if (version === commandRequestVersion) deletingRowId.value = undefined;
    }
}

function resetContext() {
    contextVersion += 1;
    tableRequestVersion += 1;
    commandRequestVersion += 1;
    tablesLoading.value = false;
    tablesError.value = undefined;
    tables.value = [];
    activeTableId.value = undefined;
    editingRow.value = undefined;
    saving.value = false;
    deletingRowId.value = undefined;
    for (const key of Object.keys(states)) delete states[Number(key)];
}

function isCurrent(context: number, archiveItemId: number) {
    return !disposed && context === contextVersion && props.archiveItemId === archiveItemId;
}

function displayValue(value: unknown) {
    return value === undefined || value === null || value === "" ? "-" : String(value);
}

function formValue(value: unknown): FormValue {
    return typeof value === "string" || typeof value === "number" ? value : null;
}

function numberFormValue(fieldCode: string) {
    const value = formValues[fieldCode];
    return typeof value === "number" ? value : undefined;
}

function setFormValue(fieldCode: string, value: unknown) {
    formValues[fieldCode] = formValue(value);
}
</script>

<template>
    <section v-if="archiveItemId" aria-label="档案明细行" class="line-rows">
        <h3>明细表</h3>
        <div v-if="tablesError" class="line-error">
            <el-alert :title="tablesError" type="error" show-icon :closable="false" />
            <el-button link type="primary" :loading="tablesLoading" @click="loadTables()"
                >重试明细表</el-button
            >
        </div>
        <div v-else-if="tablesLoading" v-loading="true" class="line-loading" />
        <el-empty v-else-if="!tables.length" description="当前分类没有可用明细表" />
        <el-tabs v-else :model-value="activeTableId" @tab-change="changeTable">
            <el-tab-pane
                v-for="table in tables"
                :key="table.id"
                :name="table.id"
                :label="table.tableName"
            >
                <template v-if="states[table.id]">
                    <div class="line-toolbar">
                        <el-button
                            v-if="!readonly"
                            type="primary"
                            :disabled="Boolean(editingRow) || commandBusy"
                            @click="startCreate"
                            >新增明细</el-button
                        >
                    </div>
                    <div v-if="states[table.id].error" class="line-error">
                        <el-alert
                            :title="states[table.id].error"
                            type="error"
                            show-icon
                            :closable="false"
                        />
                        <el-button
                            link
                            type="primary"
                            :loading="states[table.id].loading"
                            @click="loadRows(table.id)"
                            >重试明细行</el-button
                        >
                    </div>
                    <el-table
                        v-else
                        v-loading="states[table.id].loading"
                        :data="states[table.id].page?.items ?? []"
                        border
                    >
                        <el-table-column prop="lineOrder" label="顺序" width="80" />
                        <el-table-column
                            v-for="field in table.fields"
                            :key="field.id"
                            :label="field.fieldName"
                            min-width="140"
                        >
                            <template #default="{ row }">{{
                                displayValue(row.values[field.fieldCode])
                            }}</template>
                        </el-table-column>
                        <el-table-column v-if="!readonly" label="操作" width="140" fixed="right">
                            <template #default="{ row }">
                                <el-button
                                    link
                                    type="primary"
                                    :disabled="commandBusy"
                                    @click="startEdit(row)"
                                    >编辑</el-button
                                >
                                <el-button
                                    link
                                    type="danger"
                                    :loading="deletingRowId === row.id"
                                    :disabled="commandBusy"
                                    @click="removeRow(row)"
                                    >删除</el-button
                                >
                            </template>
                        </el-table-column>
                    </el-table>
                    <CursorPagination
                        v-if="states[table.id].page"
                        :limit="states[table.id].limit"
                        :prev="states[table.id].page?.prev"
                        :next="states[table.id].page?.next"
                        :loading="states[table.id].loading"
                        @page="(cursor) => loadRows(table.id, cursor)"
                        @limit-change="changeLimit"
                    />
                    <el-form
                        v-if="editingRow && activeTableId === table.id"
                        class="line-form"
                        label-position="top"
                    >
                        <el-alert
                            v-if="formError"
                            :title="formError"
                            type="error"
                            show-icon
                            :closable="false"
                        />
                        <el-form-item label="行顺序" :error="fieldErrors.lineOrder">
                            <el-input-number v-model="lineOrder" :min="0" :precision="0" />
                        </el-form-item>
                        <el-row :gutter="16">
                            <el-col v-for="field in table.fields" :key="field.id" :span="12">
                                <el-form-item
                                    :label="field.fieldName"
                                    :error="fieldErrors[field.fieldCode]"
                                >
                                    <el-input-number
                                        v-if="field.fieldType === 'INTEGER'"
                                        :model-value="numberFormValue(field.fieldCode)"
                                        :precision="0"
                                        controls-position="right"
                                        @update:model-value="
                                            (value) => setFormValue(field.fieldCode, value)
                                        "
                                    />
                                    <el-date-picker
                                        v-else-if="field.fieldType === 'DATE'"
                                        v-model="formValues[field.fieldCode]"
                                        type="date"
                                        value-format="YYYY-MM-DD"
                                    />
                                    <el-date-picker
                                        v-else-if="field.fieldType === 'DATETIME'"
                                        v-model="formValues[field.fieldCode]"
                                        type="datetime"
                                        value-format="YYYY-MM-DD HH:mm:ss"
                                    />
                                    <el-input
                                        v-else
                                        v-model="formValues[field.fieldCode]"
                                        :inputmode="
                                            field.fieldType === 'DECIMAL' ? 'decimal' : undefined
                                        "
                                    />
                                </el-form-item>
                            </el-col>
                        </el-row>
                        <div class="line-form__actions">
                            <el-button :disabled="saving" @click="cancelEdit">取消</el-button>
                            <el-button type="primary" :loading="saving" @click="saveRow"
                                >保存明细</el-button
                            >
                        </div>
                    </el-form>
                </template>
            </el-tab-pane>
        </el-tabs>
    </section>
</template>

<style scoped src="./ArchiveItemLineRows.css"></style>
