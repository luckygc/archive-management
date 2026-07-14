<script setup lang="ts">
import { Download, Edit, Files, Plus, Refresh, Upload } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";

import { errorMessage } from "@archive-management/frontend-core/api";

import {
    createArchiveRecord,
    downloadArchiveImportTemplate,
    downloadArchiveItemElectronicFile,
    exportArchiveRecords,
    getArchiveRecord,
    importArchiveRecords,
    listArchiveCategories,
    listArchiveFields,
    listArchiveFonds,
    listArchiveItemAudits,
    listArchiveItemElectronicFiles,
    listArchiveRelatedFilterCategories,
    searchArchiveRecords,
    unbindArchiveItemElectronicFile,
    updateArchiveRecord,
    uploadArchiveItemElectronicFile,
} from "@/shared/api/archive";
import type {
    ArchiveCategoryDto,
    ArchiveElectronicStatus,
    ArchiveFieldDto,
    ArchiveItemAuditDto,
    ArchiveItemElectronicFileDto,
    ArchiveRecordDetailDto,
    ArchiveRecordListDto,
    ArchiveRecordOrderBy,
    ArchiveRelatedFilterCategoryDto,
    SearchArchiveRecordsQuery,
} from "@/shared/types/archive";
import { usePermissionStore } from "@/stores/permissionStore";

import ArchiveAdvancedQueryPanel from "@/pages/archive-library/ArchiveAdvancedQueryPanel.vue";
import type { ArchiveQueryFormValues } from "@/pages/archive-library/archiveQueryTypes";
import ArchiveResultTable from "@/pages/archive-library/ArchiveResultTable.vue";
import DynamicArchiveFields, {
    normalizeArchiveRecordFormValues,
} from "@/pages/archive-library/DynamicArchiveFields.vue";
import { toSearchQuery } from "@/pages/archive-library/archiveQuery";

const permissionStore = usePermissionStore();
const canRead = computed(() => permissionStore.has("archive:item:read"));
const canCreate = computed(() => permissionStore.has("archive:item:create"));
const canUpdate = computed(() => permissionStore.has("archive:item:update"));
const canCreateElectronicFile = computed(() => canCreate.value || canUpdate.value);
const canDeleteElectronicFile = computed(() => permissionStore.has("archive:item:delete"));
const canDownloadFile = computed(() =>
    permissionStore.has("archive:item:download-electronic-file"),
);
const canReadAudit = computed(
    () =>
        permissionStore.superAdmin ||
        permissionStore.has("archive:item:audit:read") ||
        permissionStore.has("archive:item:read-audit"),
);
const canImport = computed(() => canCreate.value || canUpdate.value);
const canExport = computed(() => permissionStore.has("archive:export"));
const queryForm = reactive<ArchiveQueryFormValues>({ conditions: [], relatedGroups: [] });
const categories = ref<ArchiveCategoryDto[]>([]);
const fonds = ref<Array<{ fondsCode: string; fondsName: string }>>([]);
const fields = ref<ArchiveFieldDto[]>([]);
const relatedCategories = ref<ArchiveRelatedFilterCategoryDto[]>([]);
const relatedFieldsByCategory = ref(new Map<number, ArchiveFieldDto[]>());
const result = ref<ArchiveRecordListDto>();
const committedQuery = ref<SearchArchiveRecordsQuery>();
const orderBy = ref<ArchiveRecordOrderBy[]>([]);
const loading = ref(false);
const drawerState = ref<{ archiveItemId: number; activeKey: "files" | "audits" }>();
const files = ref<ArchiveItemElectronicFileDto[]>([]);
const audits = ref<ArchiveItemAuditDto[]>([]);
const fileForm = reactive({ usageType: "", displayOrder: undefined as number | undefined });
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
const importInput = ref<HTMLInputElement>();
const uploadInput = ref<HTMLInputElement>();
const editorFormRef = ref<FormInstance>();
const downloadingTemplate = ref(false);
const importing = ref(false);
const exporting = ref(false);
const drawerLoading = ref(false);
const uploading = ref(false);
const downloadingFileId = ref<number>();
const unbindingFileId = ref<number>();
const editorLoading = ref(false);
const saving = ref(false);
let categoryLoadVersion = 0;

const editorFields = computed(() =>
    editorState.value?.mode === "create" ? fields.value : (editorDetail.value?.fields ?? []),
);
onMounted(async () => {
    try {
        const [categoryResponse, fondsResponse] = await Promise.all([
            listArchiveCategories(true),
            listArchiveFonds(true),
        ]);
        categories.value = categoryResponse.items;
        fonds.value = fondsResponse.items;
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "加载档案管理基础数据失败");
    }
});
watch(
    () => queryForm.categoryId,
    async (categoryId, previous) => {
        const loadVersion = ++categoryLoadVersion;
        if (typeof categoryId !== "number") {
            fields.value = [];
            relatedCategories.value = [];
            relatedFieldsByCategory.value = new Map();
            return;
        }
        if (previous !== undefined && previous !== categoryId) {
            queryForm.conditions = [];
            queryForm.relatedGroups = [];
            result.value = undefined;
            committedQuery.value = undefined;
            orderBy.value = [];
        }
        try {
            const [fieldResponse, relatedResponse] = await Promise.all([
                listArchiveFields(categoryId, "ITEM"),
                listArchiveRelatedFilterCategories(categoryId),
            ]);
            const ids = [...new Set(relatedResponse.items.map((item) => item.categoryId))];
            const responses = await Promise.all(ids.map((id) => listArchiveFields(id, "ITEM")));
            if (loadVersion !== categoryLoadVersion || queryForm.categoryId !== categoryId) return;
            fields.value = fieldResponse.items;
            relatedCategories.value = relatedResponse.items;
            relatedFieldsByCategory.value = new Map(
                ids.map((id, index) => [id, responses[index]!.items]),
            );
        } catch (error) {
            if (loadVersion !== categoryLoadVersion) return;
            fields.value = [];
            relatedCategories.value = [];
            relatedFieldsByCategory.value = new Map();
            ElMessage.error(error instanceof Error ? error.message : "加载查询字段失败");
        }
    },
);

async function execute(query: SearchArchiveRecordsQuery) {
    loading.value = true;
    try {
        result.value = await searchArchiveRecords(query);
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "查询失败");
    } finally {
        loading.value = false;
    }
}
function submit(values: ArchiveQueryFormValues) {
    const query = { ...toSearchQuery(values), keyword: undefined };
    committedQuery.value = query;
    orderBy.value = [];
    void execute(query);
}
function refresh() {
    if (committedQuery.value)
        void execute({
            ...committedQuery.value,
            orderBy: orderBy.value.length ? orderBy.value : undefined,
        });
}
function reset() {
    Object.assign(queryForm, {
        categoryId: undefined,
        fondsCode: undefined,
        keyword: undefined,
        conditions: [],
        relatedGroups: [],
    });
    result.value = undefined;
    committedQuery.value = undefined;
    orderBy.value = [];
}
function orderResults(next: ArchiveRecordOrderBy[]) {
    if (!committedQuery.value) return;
    orderBy.value = next;
    void execute({ ...committedQuery.value, orderBy: next.length ? next : undefined });
}
function openLink(href: string) {
    const anchor = document.createElement("a");
    anchor.href = href;
    anchor.click();
}
async function downloadTemplate() {
    if (!queryForm.categoryId) return ElMessage.warning("请先选择档案分类");
    downloadingTemplate.value = true;
    try {
        openLink((await downloadArchiveImportTemplate(queryForm.categoryId)).href);
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "下载模板失败");
    } finally {
        downloadingTemplate.value = false;
    }
}
async function importFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
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
            refresh();
        }
    } catch (error) {
        ElMessage.error(errorMessage(error, "导入档案失败"));
    } finally {
        importing.value = false;
        input.value = "";
    }
}
async function exportCurrent() {
    const query = committedQuery.value ?? { ...toSearchQuery(queryForm), keyword: undefined };
    exporting.value = true;
    try {
        openLink(
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
function rowId(value: unknown) {
    const id = (value as Record<string, unknown>).id;
    return typeof id === "number" ? id : undefined;
}
async function openDrawer(value: unknown, activeKey: "files" | "audits") {
    const id = rowId(value);
    if (!id) return;
    drawerState.value = { archiveItemId: id, activeKey };
    await loadDrawer();
}
async function loadDrawer() {
    if (!drawerState.value) return;
    const state = { ...drawerState.value };
    drawerLoading.value = true;
    try {
        if (state.activeKey === "files") {
            const response = await listArchiveItemElectronicFiles(state.archiveItemId);
            if (drawerState.value?.archiveItemId === state.archiveItemId)
                files.value = response.items;
        } else {
            const response = await listArchiveItemAudits({
                archiveItemId: state.archiveItemId,
                limit: 20,
                requestTotal: true,
            });
            if (drawerState.value?.archiveItemId === state.archiveItemId)
                audits.value = response.items;
        }
    } catch (error) {
        ElMessage.error(errorMessage(error, "加载档案关联信息失败"));
    } finally {
        drawerLoading.value = false;
    }
}
async function changeDrawerTab(value: string | number) {
    if (!drawerState.value) return;
    drawerState.value.activeKey = String(value) as "files" | "audits";
    await loadDrawer();
}
async function uploadElectronicFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !drawerState.value) return;
    uploading.value = true;
    try {
        await uploadArchiveItemElectronicFile(drawerState.value.archiveItemId, file, {
            usageType: fileForm.usageType || undefined,
            displayOrder: fileForm.displayOrder,
        });
        ElMessage.success("附件已上传");
        await loadDrawer();
    } catch (error) {
        ElMessage.error(errorMessage(error, "上传附件失败"));
    } finally {
        uploading.value = false;
        input.value = "";
    }
}
async function downloadFile(id: number) {
    if (!drawerState.value) return;
    downloadingFileId.value = id;
    try {
        openLink(
            (await downloadArchiveItemElectronicFile(drawerState.value.archiveItemId, id)).href,
        );
    } catch (error) {
        ElMessage.error(errorMessage(error, "下载附件失败"));
    } finally {
        downloadingFileId.value = undefined;
    }
}
async function unbindFile(id: number) {
    if (!drawerState.value) return;
    unbindingFileId.value = id;
    try {
        await unbindArchiveItemElectronicFile(drawerState.value.archiveItemId, id);
        ElMessage.success("文件已解绑");
        await loadDrawer();
    } catch (error) {
        ElMessage.error(errorMessage(error, "解绑文件失败"));
    } finally {
        unbindingFileId.value = undefined;
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
    if (!editorState.value || !(await editorFormRef.value?.validate().catch(() => false))) return;
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
        refresh();
    } catch (error) {
        ElMessage.error(errorMessage(error, mode === "create" ? "创建档案失败" : "更新档案失败"));
    } finally {
        saving.value = false;
    }
}
function formatSize(size: number) {
    return size < 1024
        ? `${size} B`
        : size < 1024 * 1024
          ? `${(size / 1024).toFixed(1)} KB`
          : `${(size / 1024 / 1024).toFixed(1)} MB`;
}
function formatValue(value: unknown) {
    return value == null || value === "" ? "-" : String(value);
}
const detailFields = computed(() =>
    [...(editorDetail.value?.fields ?? [])]
        .filter((field) => field.enabled && field.detailVisible)
        .sort(
            (left, right) =>
                left.detailSortOrder - right.detailSortOrder || left.sortOrder - right.sortOrder,
        ),
);
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>档案管理</h1>
            <div>
                <el-button :icon="Refresh" @click="refresh">刷新</el-button
                ><el-button
                    :disabled="!queryForm.categoryId || !canImport || downloadingTemplate"
                    :loading="downloadingTemplate"
                    :icon="Download"
                    @click="downloadTemplate"
                    >导入模板</el-button
                ><el-button
                    :disabled="!queryForm.categoryId || !canImport || importing"
                    :loading="importing"
                    :icon="Upload"
                    @click="importInput?.click()"
                    >导入</el-button
                ><input
                    ref="importInput"
                    hidden
                    type="file"
                    accept=".xlsx"
                    @change="importFile"
                /><el-button
                    :disabled="!canExport || exporting"
                    :loading="exporting"
                    :icon="Download"
                    @click="exportCurrent"
                    >导出</el-button
                ><el-button
                    :disabled="!canCreate || !queryForm.categoryId"
                    :icon="Plus"
                    type="primary"
                    @click="openCreateEditor"
                    >新建档案</el-button
                >
            </div>
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
            ><ArchiveResultTable
                v-if="result"
                :result="result"
                :loading="loading"
                :order-by="orderBy"
                show-lock-column
                show-actions
                @order-change="orderResults"
                ><template #actions="{ row }"
                    ><el-button link :disabled="!canRead" @click="openRecordEditor(row, 'detail')"
                        >查看</el-button
                    ><el-button
                        link
                        :disabled="!canUpdate || row.locked_flag"
                        @click="openRecordEditor(row, 'edit')"
                        >编辑</el-button
                    ><el-button link :disabled="!canRead" @click="openDrawer(row, 'files')"
                        >文件</el-button
                    ><el-button link :disabled="!canReadAudit" @click="openDrawer(row, 'audits')"
                        >审计</el-button
                    ></template
                ></ArchiveResultTable
            ><el-empty v-else description="选择分类并提交高级查询后显示管理列表"
        /></el-card>
        <el-drawer
            :model-value="Boolean(drawerState)"
            v-loading="drawerLoading"
            @close="drawerState = undefined"
            :title="drawerState ? `档案 ${drawerState.archiveItemId}` : ''"
            size="70%"
            ><el-tabs :model-value="drawerState?.activeKey" @tab-change="changeDrawerTab"
                ><el-tab-pane label="电子文件" name="files"
                    ><div class="file-toolbar">
                        <el-input
                            v-model="fileForm.usageType"
                            :disabled="!canCreateElectronicFile"
                            placeholder="用途"
                        /><el-input-number
                            v-model="fileForm.displayOrder"
                            :disabled="!canCreateElectronicFile"
                            placeholder="顺序"
                        /><el-button
                            type="primary"
                            :disabled="!canCreateElectronicFile || uploading"
                            :loading="uploading"
                            :icon="Upload"
                            @click="uploadInput?.click()"
                            >上传附件</el-button
                        ><input
                            ref="uploadInput"
                            hidden
                            type="file"
                            @change="uploadElectronicFile"
                        />
                    </div>
                    <el-table :data="files" row-key="id"
                        ><el-table-column label="文件名" prop="originalFilename" /><el-table-column
                            label="大小"
                            width="100"
                            ><template #default="{ row }">{{
                                formatSize(row.fileSize)
                            }}</template></el-table-column
                        ><el-table-column
                            label="用途"
                            prop="usageType"
                            width="100"
                        /><el-table-column label="操作" width="140"
                            ><template #default="{ row }"
                                ><el-button
                                    link
                                    :disabled="!canDownloadFile || downloadingFileId === row.id"
                                    :loading="downloadingFileId === row.id"
                                    @click="downloadFile(row.id)"
                                    >下载</el-button
                                ><el-button
                                    link
                                    type="danger"
                                    :disabled="
                                        !canDeleteElectronicFile || unbindingFileId === row.id
                                    "
                                    :loading="unbindingFileId === row.id"
                                    @click="unbindFile(row.id)"
                                    >解绑</el-button
                                ></template
                            ></el-table-column
                        ></el-table
                    ></el-tab-pane
                ><el-tab-pane label="审计记录" name="audits"
                    ><el-table :data="audits" row-key="id"
                        ><el-table-column
                            label="操作"
                            prop="operationType"
                            width="120" /><el-table-column
                            label="原因"
                            prop="operationReason" /><el-table-column
                            label="操作人"
                            prop="operatedBy"
                            width="120" /><el-table-column
                            label="时间"
                            prop="operatedAt"
                            width="180" /></el-table></el-tab-pane></el-tabs
        ></el-drawer>
        <el-drawer
            :model-value="Boolean(editorState)"
            v-loading="editorLoading"
            @close="editorState = undefined"
            :title="
                editorState?.mode === 'create'
                    ? '新建档案'
                    : editorState?.mode === 'detail'
                      ? `档案 ${editorState.archiveItemId} 详情`
                      : `编辑档案 ${editorState?.archiveItemId}`
            "
            size="70%"
            ><template v-if="editorState?.mode === 'detail' && editorDetail"
                ><el-descriptions border :column="2"
                    ><el-descriptions-item label="档案 ID">{{
                        editorDetail.item.id
                    }}</el-descriptions-item
                    ><el-descriptions-item label="档案分类">{{
                        editorDetail.category.categoryName
                    }}</el-descriptions-item
                    ><el-descriptions-item label="全宗">{{
                        editorDetail.item.fondsName
                    }}</el-descriptions-item
                    ><el-descriptions-item label="档号">{{
                        editorDetail.item.archiveNo || "-"
                    }}</el-descriptions-item
                    ><el-descriptions-item label="年度">{{
                        editorDetail.item.archiveYear
                    }}</el-descriptions-item
                    ><el-descriptions-item label="电子状态">{{
                        editorDetail.item.electronicStatus
                    }}</el-descriptions-item
                    ><el-descriptions-item label="锁定">{{
                        editorDetail.item.lockedFlag ? "是" : "否"
                    }}</el-descriptions-item
                    ><el-descriptions-item label="锁定原因">{{
                        editorDetail.item.lockReason || "-"
                    }}</el-descriptions-item></el-descriptions
                >
                <h3>动态字段</h3>
                <el-empty
                    v-if="detailFields.length === 0"
                    description="无可展示字段"
                    :image-size="48"
                />
                <el-descriptions v-else border :column="2"
                    ><el-descriptions-item
                        v-for="field in detailFields"
                        :key="field.id"
                        :label="field.fieldName"
                        >{{
                            formatValue(editorDetail.dynamicFields[field.fieldCode])
                        }}</el-descriptions-item
                    ></el-descriptions
                ></template
            ><el-form
                v-else-if="editorState && editorState.mode !== 'detail'"
                ref="editorFormRef"
                :model="editorForm"
                label-position="top"
                ><el-form-item label="档案分类"
                    ><el-input
                        disabled
                        :model-value="
                            categories.find((item) => item.id === editorForm.categoryId)
                                ?.categoryName
                        " /></el-form-item
                ><el-form-item
                    label="全宗"
                    prop="fondsCode"
                    :rules="[{ required: true, message: '请选择全宗', trigger: 'change' }]"
                    ><el-select v-model="editorForm.fondsCode" filterable
                        ><el-option
                            v-for="item in fonds"
                            :key="item.fondsCode"
                            :label="`${item.fondsCode} ${item.fondsName}`"
                            :value="item.fondsCode" /></el-select></el-form-item
                ><el-row :gutter="16"
                    ><el-col :span="8"
                        ><el-form-item label="档号"
                            ><el-input v-model="editorForm.archiveNo" /></el-form-item></el-col
                    ><el-col :span="8"
                        ><el-form-item label="年度"
                            ><el-input-number
                                v-model="editorForm.archiveYear"
                                :min="1" /></el-form-item></el-col
                    ><el-col :span="8"
                        ><el-form-item label="电子状态"
                            ><el-select v-model="editorForm.electronicStatus"
                                ><el-option label="草稿" value="DRAFT" /><el-option
                                    label="已归档"
                                    value="ARCHIVED" /><el-option
                                    label="借出"
                                    value="BORROWED" /></el-select></el-form-item></el-col></el-row
                ><DynamicArchiveFields
                    v-model="editorForm.dynamicFields"
                    :fields="editorFields"
                /><el-button :disabled="saving" @click="editorState = undefined">取消</el-button
                ><el-button :loading="saving" type="primary" @click="saveRecord"
                    >保存</el-button
                ></el-form
            ></el-drawer
        >
    </section>
</template>

<style scoped>
.file-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
}
.file-toolbar .el-input {
    width: 180px;
}
</style>
