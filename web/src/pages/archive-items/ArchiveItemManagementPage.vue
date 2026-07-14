<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, reactive, ref } from "vue";

import { errorMessage } from "@archive-management/frontend-core/api";

import {
    createArchiveRecord,
    downloadArchiveImportTemplate,
    exportArchiveRecords,
    getArchiveRecord,
    importArchiveRecords,
    updateArchiveRecord,
} from "@/shared/api/archive-records";
import type {
    ArchiveElectronicStatus,
    ArchiveRecordDetailDto,
} from "@/shared/types/archive-records";
import CursorPagination from "@/shared/components/CursorPagination.vue";
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
const editorForm = reactive({
    categoryId: undefined as number | undefined,
    fondsCode: "",
    archiveNo: "",
    archiveYear: new Date().getFullYear(),
    electronicStatus: "DRAFT" as ArchiveElectronicStatus,
    dynamicFields: {} as Record<string, unknown>,
});
const downloadingTemplate = ref(false);
const importing = ref(false);
const exporting = ref(false);
const editorLoading = ref(false);
const saving = ref(false);
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
    drawerState,
    fileForm,
    files,
    openDrawer,
    unbindingFileId,
    unbindFile,
    uploading,
    uploadElectronicFile,
} = useArchiveItemResources(downloadFromLink);
const { busyAction, lock, remove, unlock } = useArchiveItemLifecycle(refresh);

const editorFields = computed(() =>
    editorState.value?.mode === "create" ? fields.value : (editorDetail.value?.fields ?? []),
);
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
    Object.assign(editorForm, {
        categoryId: queryForm.categoryId,
        fondsCode: "",
        archiveNo: "",
        archiveYear: new Date().getFullYear(),
        electronicStatus: "DRAFT",
        dynamicFields: {},
    });
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
    editorState.value = { mode, archiveItemId: id };
    editorDetail.value = undefined;
    editorLoading.value = true;
    try {
        const detail = await getArchiveRecord(id, mode === "detail" ? "DETAIL" : "EDIT");
        if (editorState.value?.archiveItemId !== id || editorState.value.mode !== mode) return;
        editorDetail.value = detail;
        if (mode === "edit")
            Object.assign(editorForm, {
                categoryId: detail.category.id,
                fondsCode: detail.item.fondsCode,
                archiveNo: detail.item.archiveNo ?? "",
                archiveYear: detail.item.archiveYear,
                electronicStatus: detail.item.electronicStatus,
                dynamicFields: { ...detail.dynamicFields },
            });
    } catch (error) {
        ElMessage.error(errorMessage(error, "加载档案详情失败"));
        editorState.value = undefined;
    } finally {
        editorLoading.value = false;
    }
}
async function saveRecord() {
    if (!editorState.value) return;
    const common = {
        fondsCode: editorForm.fondsCode,
        archiveNo: editorForm.archiveNo.trim() || undefined,
        archiveYear: editorForm.archiveYear,
        electronicStatus: editorForm.electronicStatus,
        physicalFields: {},
        dynamicFields: normalizeArchiveRecordFormValues({ dynamicFields: editorForm.dynamicFields })
            .dynamicFields,
    };
    const mode = editorState.value.mode;
    saving.value = true;
    try {
        if (mode === "create")
            await createArchiveRecord({ ...common, categoryId: editorForm.categoryId! });
        else if (mode === "edit")
            await updateArchiveRecord(editorState.value.archiveItemId!, common);
        ElMessage.success(mode === "create" ? "档案已创建" : "档案已更新");
        editorState.value = undefined;
        await refresh();
    } catch (error) {
        ElMessage.error(errorMessage(error, mode === "create" ? "创建档案失败" : "更新档案失败"));
    } finally {
        saving.value = false;
    }
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
            ><el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false"
                ><el-button link :loading="loading" @click="void refresh()"
                    >重试</el-button
                ></el-alert
            ><ArchiveResultTable
                v-else-if="result"
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
                        @audits="openDrawer(row, 'audits')"
                        @lock="lock(rowId(row)!)"
                        @unlock="unlock(rowId(row)!)"
                        @delete="remove(rowId(row)!)" /></template></ArchiveResultTable
            ><el-empty v-else description="选择分类并提交高级查询后显示管理列表" />
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
            :file-form="fileForm"
            :files="files"
            :audits="audits"
            :can-create-file="canCreateElectronicFile"
            :can-delete-file="canDeleteElectronicFile"
            :can-download-file="canDownloadFile"
            :uploading="uploading"
            :downloading-file-id="downloadingFileId"
            :unbinding-file-id="unbindingFileId"
            @close="drawerState = undefined"
            @tab-change="changeDrawerTab"
            @upload="uploadElectronicFile"
            @download="downloadFile"
            @unbind="unbindFile"
        />
        <ArchiveItemEditorDrawer
            :state="editorState"
            :detail="editorDetail"
            :form="editorForm"
            :fields="editorFields"
            :categories="categories"
            :fonds="fonds"
            :loading="editorLoading"
            :saving="saving"
            @close="editorState = undefined"
            @save="saveRecord"
        />
    </section>
</template>
