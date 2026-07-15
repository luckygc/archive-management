<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";

import { errorMessage, HttpClientError } from "@archive-management/frontend-core/api";

import {
    listArchiveRetentionPeriods,
    listArchiveSecurityLevels,
} from "@/shared/api/archive-metadata";
import {
    createArchiveRecord,
    downloadArchiveImportTemplate,
    exportArchiveRecords,
    getArchiveRecord,
    importArchiveRecords,
    updateArchiveRecord,
} from "@/shared/api/archive-records";
import type {
    ArchiveRetentionPeriodDto,
    ArchiveSecurityLevelDto,
} from "@/shared/types/archive-metadata";
import type {
    ArchiveElectronicStatus,
    ArchiveRecordDetailDto,
} from "@/shared/types/archive-records";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import { usePermissionStore } from "@/stores/permissionStore";

import ArchiveAdvancedQueryPanel from "@/pages/archive-library/ArchiveAdvancedQueryPanel.vue";
import { toSearchQuery } from "@/pages/archive-library/archiveQuery";
import ArchiveResultTable from "@/pages/archive-library/ArchiveResultTable.vue";
import { normalizeArchiveRecordFormValues } from "@/pages/archive-library/DynamicArchiveFields.vue";
import { useArchiveItemResources } from "./useArchiveItemResources";
import ArchiveItemActions from "./ArchiveItemActions.vue";
import ArchiveItemEditorDrawer from "./ArchiveItemEditorDrawer.vue";
import ArchiveItemResourcesDrawer from "./ArchiveItemResourcesDrawer.vue";
import ArchiveItemRowActions from "./ArchiveItemRowActions.vue";
import { downloadFromLink } from "./downloadFromLink";
import { useArchiveItemLifecycle } from "./useArchiveItemLifecycle";
import { useArchiveItemSearch } from "./useArchiveItemSearch";

const permissionStore = usePermissionStore();
const canRead = computed(() => permissionStore.has("archive:item:read"));
const canCreate = computed(() => permissionStore.has("archive:item:create"));
const canUpdate = computed(() => permissionStore.has("archive:item:update"));
const canLock = computed(() => permissionStore.has("archive:item:lock"));
const canDelete = computed(() => permissionStore.has("archive:item:delete"));
const canImport = computed(() => canCreate.value || canUpdate.value);
const canExport = computed(() => permissionStore.has("archive:export"));
const {
    categories,
    committedQuery,
    fields,
    fonds,
    limit,
    limitChange,
    loading,
    loadError,
    orderBy,
    orderResults,
    page,
    queryForm,
    refresh,
    relatedCategories,
    relatedFieldsByCategory,
    reset,
    result,
    submit,
} = useArchiveItemSearch();
const editorState = ref<{ mode: "create" | "detail" | "edit"; archiveItemId?: number }>();
const editorDetail = ref<ArchiveRecordDetailDto>();
const securityLevels = ref<ArchiveSecurityLevelDto[]>([]);
const retentionPeriods = ref<ArchiveRetentionPeriodDto[]>([]);
const editorFieldErrors = reactive<Record<string, string>>({});
const editorFormError = ref<string>();
const editorForm = reactive({
    categoryId: undefined as number | undefined,
    fondsCode: "",
    archiveNo: "",
    archiveYear: new Date().getFullYear(),
    electronicStatus: "DRAFT" as ArchiveElectronicStatus,
    securityLevelId: undefined as number | undefined,
    retentionPeriodId: undefined as number | undefined,
    physicalFields: {} as Record<string, unknown>,
    dynamicFields: {} as Record<string, unknown>,
});
const downloadingTemplate = ref(false);
const importing = ref(false);
const exporting = ref(false);
const editorLoading = ref(false);
const referencesLoading = ref(false);
const referencesLoadError = ref<string>();
const saving = ref(false);
let editorRequestVersion = 0;
let referencesRequestVersion = 0;
const {
    audits,
    canCreateElectronicFile,
    canDeleteElectronicFile,
    canDownloadFile,
    canReadAudit,
    changeDrawerTab,
    downloadingFileId,
    downloadFile,
    drawerLoading,
    drawerLoadError,
    drawerState,
    fileForm,
    files,
    openDrawer,
    retryDrawer,
    unbindingFileId,
    unbindFile,
    uploading,
    uploadElectronicFile,
} = useArchiveItemResources(downloadFromLink);
const { busyAction, lock, remove, unlock } = useArchiveItemLifecycle(refresh);

const editorFields = computed(() =>
    editorState.value?.mode === "create"
        ? fields.value.filter((field) => field.fieldScope === "METADATA")
        : (editorDetail.value?.fields ?? []),
);
const editorPhysicalFields = computed(() =>
    editorState.value?.mode === "create"
        ? fields.value.filter((field) => field.fieldScope === "PHYSICAL")
        : (editorDetail.value?.physicalFields ?? []),
);

onMounted(loadEditorReferences);

async function loadEditorReferences() {
    const requestVersion = ++referencesRequestVersion;
    referencesLoading.value = true;
    referencesLoadError.value = undefined;
    try {
        const [securityResult, retentionResult] = await Promise.allSettled([
            listArchiveSecurityLevels(true),
            listArchiveRetentionPeriods(true),
        ]);
        if (requestVersion !== referencesRequestVersion) return;
        const errors: string[] = [];
        if (securityResult.status === "fulfilled")
            securityLevels.value = securityResult.value.items.filter((item) => item.enabled);
        else errors.push(errorMessage(securityResult.reason, "加载密级失败"));
        if (retentionResult.status === "fulfilled")
            retentionPeriods.value = retentionResult.value.items.filter((item) => item.enabled);
        else errors.push(errorMessage(retentionResult.reason, "加载保管期限失败"));
        referencesLoadError.value = errors.length ? errors.join("；") : undefined;
    } finally {
        if (requestVersion === referencesRequestVersion) referencesLoading.value = false;
    }
}
async function downloadTemplate() {
    if (!queryForm.categoryId) return ElMessage.warning("请先选择档案分类");
    downloadingTemplate.value = true;
    try {
        downloadFromLink((await downloadArchiveImportTemplate(queryForm.categoryId)).href);
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "下载模板失败");
    } finally {
        downloadingTemplate.value = false;
    }
}
async function importFile(file: File) {
    if (!file || !queryForm.categoryId) return;
    importing.value = true;
    try {
        const response = await importArchiveRecords(queryForm.categoryId, file);
        if (response.errors.length)
            await ElMessageBox.alert(
                response.errors
                    .slice(0, 20)
                    .map((item) => `第 ${item.rowNumber} 行 ${item.fieldName}：${item.message}`)
                    .join("\n"),
                "导入校验未通过",
            );
        else {
            ElMessage.success(`已导入 ${response.importedCount} 条档案`);
            await refresh();
        }
    } catch (error) {
        ElMessage.error(errorMessage(error, "导入档案失败"));
    } finally {
        importing.value = false;
    }
}
async function exportCurrent() {
    const query = committedQuery.value ?? { ...toSearchQuery(queryForm), keyword: undefined };
    exporting.value = true;
    try {
        downloadFromLink(
            (
                await exportArchiveRecords({
                    ...query,
                    orderBy: orderBy.value.length ? orderBy.value : undefined,
                })
            ).href,
        );
    } catch (error) {
        ElMessage.error(errorMessage(error, "导出档案失败"));
    } finally {
        exporting.value = false;
    }
}
function openCreateEditor() {
    if (!queryForm.categoryId) return;
    invalidateEditorRequest();
    Object.assign(editorForm, {
        categoryId: queryForm.categoryId,
        fondsCode: "",
        archiveNo: "",
        archiveYear: new Date().getFullYear(),
        electronicStatus: "DRAFT",
        securityLevelId: undefined,
        retentionPeriodId: undefined,
        physicalFields: {},
        dynamicFields: {},
    });
    clearEditorFieldErrors();
    editorDetail.value = undefined;
    editorState.value = { mode: "create" };
}
function rowId(value: unknown) {
    const id = (value as Record<string, unknown>).id;
    return typeof id === "number" ? id : undefined;
}
async function openRecordEditor(value: unknown, mode: "detail" | "edit") {
    const id = rowId(value);
    if (!id) return;
    const requestVersion = ++editorRequestVersion;
    editorState.value = { mode, archiveItemId: id };
    editorDetail.value = undefined;
    clearEditorFieldErrors();
    editorLoading.value = true;
    try {
        const detail = await getArchiveRecord(id, mode === "detail" ? "DETAIL" : "EDIT");
        if (requestVersion !== editorRequestVersion) return;
        editorDetail.value = detail;
        Object.assign(editorForm, {
            categoryId: detail.category.id,
            fondsCode: detail.item.fondsCode,
            archiveNo: detail.item.archiveNo ?? "",
            archiveYear: detail.item.archiveYear,
            electronicStatus: detail.item.electronicStatus,
            securityLevelId: detail.item.securityLevelId,
            retentionPeriodId: detail.item.retentionPeriodId,
            physicalFields: { ...detail.physicalFieldValues },
            dynamicFields: { ...detail.dynamicFields },
        });
    } catch (error) {
        if (requestVersion !== editorRequestVersion) return;
        ElMessage.error(errorMessage(error, "加载档案详情失败"));
        editorState.value = undefined;
    } finally {
        if (requestVersion === editorRequestVersion) editorLoading.value = false;
    }
}
async function saveRecord() {
    if (!editorState.value) return;
    const common = {
        fondsCode: editorForm.fondsCode,
        archiveNo: editorForm.archiveNo.trim() || undefined,
        archiveYear: editorForm.archiveYear,
        electronicStatus: editorForm.electronicStatus,
        securityLevelId: editorForm.securityLevelId,
        retentionPeriodId: editorForm.retentionPeriodId,
        physicalFields: normalizeArchiveRecordFormValues({
            dynamicFields: editorForm.physicalFields,
        }).dynamicFields,
        dynamicFields: normalizeArchiveRecordFormValues({ dynamicFields: editorForm.dynamicFields })
            .dynamicFields,
    };
    const mode = editorState.value.mode;
    clearEditorFieldErrors();
    saving.value = true;
    try {
        if (mode === "create")
            await createArchiveRecord({ ...common, categoryId: editorForm.categoryId! });
        else if (mode === "edit")
            await updateArchiveRecord(editorState.value.archiveItemId!, common);
        ElMessage.success(mode === "create" ? "档案已创建" : "档案已更新");
        closeEditor();
        await refresh();
    } catch (error) {
        applyEditorFieldViolations(error);
        const fallback = mode === "create" ? "创建档案失败" : "更新档案失败";
        const message = errorMessage(error, fallback);
        ElMessage.error(
            error instanceof HttpClientError && error.traceId
                ? `${message}（追踪 ID：${error.traceId}）`
                : message,
        );
    } finally {
        saving.value = false;
    }
}

function closeEditor() {
    invalidateEditorRequest();
}

function clearEditorFieldErrors() {
    for (const field of Object.keys(editorFieldErrors)) delete editorFieldErrors[field];
    editorFormError.value = undefined;
}

function invalidateEditorRequest() {
    editorRequestVersion += 1;
    editorLoading.value = false;
    editorState.value = undefined;
    editorDetail.value = undefined;
    clearEditorFieldErrors();
}

function applyEditorFieldViolations(error: unknown) {
    if (!(error instanceof HttpClientError)) return;
    const physicalFieldCodes = new Set(editorPhysicalFields.value.map((field) => field.fieldCode));
    const dynamicFieldCodes = new Set(editorFields.value.map((field) => field.fieldCode));
    const fixedFields = new Set([
        "categoryId",
        "fondsCode",
        "archiveNo",
        "archiveYear",
        "electronicStatus",
        "securityLevelId",
        "retentionPeriodId",
    ]);
    const formErrors: string[] = [];
    for (const violation of error.fieldViolations) {
        if (!violation.field || !violation.message) continue;
        const field = violation.field;
        let targetField: string | undefined;
        if (field.startsWith("physicalFields.")) {
            const fieldCode = field.slice("physicalFields.".length);
            if (physicalFieldCodes.has(fieldCode)) targetField = field;
        } else if (field.startsWith("dynamicFields.")) {
            const fieldCode = field.slice("dynamicFields.".length);
            if (dynamicFieldCodes.has(fieldCode)) targetField = field;
        } else if (fixedFields.has(field)) targetField = field;
        if (targetField)
            editorFieldErrors[targetField] = editorFieldErrors[targetField]
                ? `${editorFieldErrors[targetField]}；${violation.message}`
                : violation.message;
        else formErrors.push(violation.message);
    }
    editorFormError.value = [...new Set(formErrors)].join("；") || undefined;
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>档案管理</h1>
            <ArchiveItemActions
                :category-selected="Boolean(queryForm.categoryId)"
                :can-import="canImport"
                :can-export="canExport"
                :can-create="canCreate"
                :downloading-template="downloadingTemplate"
                :importing="importing"
                :exporting="exporting"
                @refresh="void refresh()"
                @download-template="downloadTemplate"
                @import-file="importFile"
                @export="exportCurrent"
                @create="openCreateEditor"
            />
        </div>
        <el-collapse class="am-page__filter" :model-value="['query']"
            ><el-collapse-item title="高级筛选条件" name="query"
                ><ArchiveAdvancedQueryPanel
                    :model-value="queryForm"
                    @update:model-value="Object.assign(queryForm, $event)"
                    :categories="categories"
                    :fields="fields"
                    :related-categories="relatedCategories"
                    :related-fields-by-category="relatedFieldsByCategory"
                    :show-keyword="false"
                    :submitting="loading"
                    @submit="submit"
                    @reset="reset" /></el-collapse-item
        ></el-collapse>
        <el-card class="am-page__result" shadow="never"
            ><RequestErrorState
                v-if="loadError"
                :message="loadError"
                :retrying="loading"
                @retry="void refresh()" /><ArchiveResultTable
                v-if="result"
                :result="result"
                :loading="loading"
                :order-by="orderBy"
                show-lock-column
                show-actions
                @order-change="orderResults"
                ><template #actions="{ row }"
                    ><ArchiveItemRowActions
                        :locked="row.locked_flag === true"
                        :can-read="canRead"
                        :can-update="canUpdate"
                        :can-lock="canLock"
                        :can-delete="canDelete"
                        :can-read-audit="canReadAudit"
                        :busy="Boolean(busyAction)"
                        @view="openRecordEditor(row, 'detail')"
                        @edit="openRecordEditor(row, 'edit')"
                        @files="openDrawer(row, 'files')"
                        @relations="openDrawer(row, 'relations')"
                        @audits="openDrawer(row, 'audits')"
                        @lock="lock(rowId(row)!)"
                        @unlock="unlock(rowId(row)!)"
                        @delete="remove(rowId(row)!)" /></template></ArchiveResultTable
            ><el-empty v-else-if="!loadError" description="选择分类并提交高级查询后显示管理列表" />
            <div v-if="result" class="am-table-footer">
                <CursorPagination
                    :limit="limit"
                    :prev="result.prev"
                    :next="result.next"
                    :loading="loading"
                    @page="page"
                    @limit-change="limitChange"
                /></div
        ></el-card>
        <ArchiveItemResourcesDrawer
            :state="drawerState"
            :loading="drawerLoading"
            :load-error="drawerLoadError"
            :file-form="fileForm"
            :files="files"
            :audits="audits"
            :can-create-file="canCreateElectronicFile"
            :can-delete-file="canDeleteElectronicFile"
            :can-download-file="canDownloadFile"
            :can-read-relation="canRead"
            :can-update-relation="canUpdate"
            :uploading="uploading"
            :downloading-file-id="downloadingFileId"
            :unbinding-file-id="unbindingFileId"
            @close="drawerState = undefined"
            @tab-change="changeDrawerTab"
            @upload="uploadElectronicFile"
            @download="downloadFile"
            @unbind="unbindFile"
            @retry="void retryDrawer()"
        />
        <ArchiveItemEditorDrawer
            :state="editorState"
            :detail="editorDetail"
            :form="editorForm"
            :fields="editorFields"
            :physical-fields="editorPhysicalFields"
            :categories="categories"
            :fonds="fonds"
            :security-levels="securityLevels"
            :retention-periods="retentionPeriods"
            :field-errors="editorFieldErrors"
            :form-error="editorFormError"
            :loading="editorLoading"
            :references-loading="referencesLoading"
            :references-load-error="referencesLoadError"
            :saving="saving"
            @close="closeEditor"
            @retry-references="loadEditorReferences"
            @save="saveRecord"
        />
    </section>
</template>
