<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";

import {
    buildArchiveCategoryTable,
    createArchiveCategory,
    createArchiveField,
    deleteArchiveCategory,
    deleteArchiveField,
    listArchiveCategories,
    listArchiveClassificationSchemes,
    listArchiveFields,
    listArchiveFonds,
    listArchiveFondsCategoryScopes,
    saveArchiveFondsCategoryScopes,
    updateArchiveCategory,
    updateArchiveField,
} from "@/shared/api/archive-metadata";
import type {
    ArchiveCategoryDto,
    ArchiveClassificationSchemeDto,
    ArchiveFieldDto,
    ArchiveFieldType,
    ArchiveFondsCategoryScopeRequest,
    ArchiveFondsDto,
    ArchiveLevel,
    ArchiveManagementMode,
} from "@/shared/types/archive-metadata";

const managementModeLabels: Record<ArchiveManagementMode, string> = {
    ITEM_ONLY: "仅允许著录条目",
    VOLUME_ITEM: "可建卷并著录条目",
};
const fieldTypeLabels: Record<string, string> = {
    TEXT: "文本",
    INTEGER: "整数",
    DECIMAL: "小数",
    DATE: "日期",
    DATETIME: "日期时间",
};
const categories = ref<ArchiveCategoryDto[]>([]);
const schemes = ref<ArchiveClassificationSchemeDto[]>([]);
const fonds = ref<ArchiveFondsDto[]>([]);
const fields = ref<ArchiveFieldDto[]>([]);
const selectedCategoryId = ref<number>();
const selectedSchemeId = ref<number>();
const loading = ref(false);
const saving = ref(false);
const categoryDialogOpen = ref(false);
const categoryMode = ref<"create" | "edit">("create");
const editingCategory = ref<ArchiveCategoryDto>();
const categoryForm = reactive({
    schemeId: undefined as number | undefined,
    parentId: undefined as number | undefined,
    categoryCode: "",
    categoryName: "",
    managementMode: "ITEM_ONLY" as ArchiveManagementMode,
    enabled: true,
    sortOrder: 0,
});
const fieldDialogOpen = ref(false);
const fieldMode = ref<"create" | "edit">("create");
const editingField = ref<ArchiveFieldDto>();
const fieldForm = reactive({
    archiveLevel: "ITEM" as ArchiveLevel,
    fieldCode: "",
    fieldName: "",
    fieldType: "TEXT" as ArchiveFieldType,
    textLength: undefined as number | undefined,
    decimalPrecision: undefined as number | undefined,
    decimalScale: undefined as number | undefined,
    enabled: true,
    listVisible: true,
    detailVisible: true,
    editVisible: true,
    exactSearchable: false,
    dataScopeFilterable: false,
    sortOrder: 0,
    listSortOrder: 0,
    detailSortOrder: 0,
    editSortOrder: 0,
});
const scopeDialogOpen = ref(false);
const scopeFondsCode = ref<string>();
const scopeItems = ref<ArchiveFondsCategoryScopeRequest[]>([]);

const enabledSchemes = computed(() => schemes.value.filter((item) => item.enabled));
const visibleCategories = computed(() =>
    selectedSchemeId.value == null
        ? categories.value
        : categories.value.filter((item) => item.schemeId === selectedSchemeId.value),
);
const selectedCategory = computed(() =>
    visibleCategories.value.find((item) => item.id === selectedCategoryId.value),
);
const selectedScheme = computed(() =>
    schemes.value.find((item) => item.id === selectedSchemeId.value),
);
const schemeNameById = computed(
    () => new Map(schemes.value.map((item) => [item.id, item.schemeName])),
);
const treeData = computed(() => buildTree(visibleCategories.value));
const parentOptions = computed(() =>
    categories.value.filter(
        (item) => item.schemeId === categoryForm.schemeId && item.id !== editingCategory.value?.id,
    ),
);
const scopeCategoryOptions = computed(() => categories.value.filter((item) => item.enabled));

onMounted(async () => {
    loading.value = true;
    try {
        const [categoryResponse, schemeResponse, fondsResponse] = await Promise.all([
            listArchiveCategories(),
            listArchiveClassificationSchemes(),
            listArchiveFonds(true),
        ]);
        categories.value = categoryResponse.items;
        schemes.value = schemeResponse.items;
        fonds.value = fondsResponse.items;
        const preferred = schemes.value.find((item) => item.defaultFlag) ?? schemes.value[0];
        selectedSchemeId.value = preferred?.id;
    } finally {
        loading.value = false;
    }
});
watch(
    visibleCategories,
    (items) => {
        if (!items.some((item) => item.id === selectedCategoryId.value))
            selectedCategoryId.value = items[0]?.id;
    },
    { immediate: true },
);
watch(selectedCategoryId, async (id) => {
    fields.value = id == null ? [] : (await listArchiveFields(id)).items;
});

async function reloadCategories() {
    categories.value = (await listArchiveCategories()).items;
}
function resetCategoryForm() {
    Object.assign(categoryForm, {
        schemeId: selectedSchemeId.value ?? enabledSchemes.value[0]?.id,
        parentId: undefined,
        categoryCode: "",
        categoryName: "",
        managementMode: "ITEM_ONLY",
        enabled: true,
        sortOrder: 0,
    });
}
function openCreateCategory() {
    categoryMode.value = "create";
    editingCategory.value = undefined;
    resetCategoryForm();
    categoryForm.parentId = selectedCategoryId.value;
    categoryDialogOpen.value = true;
}
function openEditCategory() {
    const item = selectedCategory.value;
    if (!item) return;
    categoryMode.value = "edit";
    editingCategory.value = item;
    Object.assign(categoryForm, {
        schemeId: item.schemeId,
        parentId: item.parentId,
        categoryCode: item.categoryCode,
        categoryName: item.categoryName,
        managementMode: item.managementMode,
        enabled: item.enabled,
        sortOrder: item.sortOrder,
    });
    categoryDialogOpen.value = true;
}
async function saveCategory() {
    if (
        !categoryForm.schemeId ||
        !categoryForm.categoryCode.trim() ||
        !categoryForm.categoryName.trim()
    )
        return ElMessage.warning("请填写分类必填项");
    saving.value = true;
    try {
        const payload = {
            ...categoryForm,
            schemeId: categoryForm.schemeId,
            parentId: categoryForm.parentId,
            categoryCode: categoryForm.categoryCode.trim(),
            categoryName: categoryForm.categoryName.trim(),
        };
        if (categoryMode.value === "create") await createArchiveCategory(payload);
        else await updateArchiveCategory(editingCategory.value!.id, payload);
        ElMessage.success(categoryMode.value === "create" ? "分类创建成功" : "分类更新成功");
        categoryDialogOpen.value = false;
        await reloadCategories();
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "保存失败");
    } finally {
        saving.value = false;
    }
}
async function removeCategory() {
    if (!selectedCategory.value) return;
    await ElMessageBox.confirm("确定删除此分类？", "确认删除", { type: "warning" });
    await deleteArchiveCategory(selectedCategory.value.id);
    selectedCategoryId.value = undefined;
    ElMessage.success("分类已删除");
    await reloadCategories();
}
async function buildTable() {
    if (!selectedCategory.value) return;
    saving.value = true;
    try {
        await buildArchiveCategoryTable(selectedCategory.value.id);
        ElMessage.success("动态表已生成");
        await reloadCategories();
    } finally {
        saving.value = false;
    }
}
function resetFieldForm() {
    Object.assign(fieldForm, {
        archiveLevel: "ITEM",
        fieldCode: "",
        fieldName: "",
        fieldType: "TEXT",
        textLength: undefined,
        decimalPrecision: undefined,
        decimalScale: undefined,
        enabled: true,
        listVisible: true,
        detailVisible: true,
        editVisible: true,
        exactSearchable: false,
        dataScopeFilterable: false,
        sortOrder: 0,
        listSortOrder: 0,
        detailSortOrder: 0,
        editSortOrder: 0,
    });
}
function openCreateField() {
    fieldMode.value = "create";
    editingField.value = undefined;
    resetFieldForm();
    fieldDialogOpen.value = true;
}
function openEditField(value: unknown) {
    const item = value as ArchiveFieldDto;
    fieldMode.value = "edit";
    editingField.value = item;
    Object.assign(fieldForm, item);
    fieldDialogOpen.value = true;
}
async function saveField() {
    if (!selectedCategoryId.value || !fieldForm.fieldCode.trim() || !fieldForm.fieldName.trim())
        return ElMessage.warning("请填写字段必填项");
    saving.value = true;
    try {
        const payload = {
            ...fieldForm,
            editControl: undefined,
            listWidth: undefined,
            detailColSpan: 1,
            editColSpan: 1,
        };
        if (fieldMode.value === "create")
            await createArchiveField(selectedCategoryId.value, payload);
        else await updateArchiveField(selectedCategoryId.value, editingField.value!.id, payload);
        fields.value = (await listArchiveFields(selectedCategoryId.value)).items;
        fieldDialogOpen.value = false;
        ElMessage.success(fieldMode.value === "create" ? "字段创建成功" : "字段更新成功");
    } finally {
        saving.value = false;
    }
}
async function removeField(value: unknown) {
    const item = value as ArchiveFieldDto;
    await ElMessageBox.confirm("确定删除此字段？", "确认删除", { type: "warning" });
    await deleteArchiveField(item.categoryId, item.id);
    fields.value = (await listArchiveFields(item.categoryId)).items;
    ElMessage.success("字段已删除");
}
async function openScope() {
    scopeDialogOpen.value = true;
    scopeFondsCode.value = fonds.value[0]?.fondsCode;
    await loadScopes();
}
async function loadScopes() {
    scopeItems.value = scopeFondsCode.value
        ? (await listArchiveFondsCategoryScopes(scopeFondsCode.value)).items.map((item) => ({
              categoryId: item.categoryId,
              defaultFlag: item.defaultFlag,
              sortOrder: item.sortOrder,
          }))
        : [];
}
async function saveScopes() {
    if (!scopeFondsCode.value) return;
    saving.value = true;
    try {
        await saveArchiveFondsCategoryScopes(scopeFondsCode.value, scopeItems.value);
        scopeDialogOpen.value = false;
        ElMessage.success("全宗可用分类已保存");
    } finally {
        saving.value = false;
    }
}

function buildTree(items: ArchiveCategoryDto[]) {
    const map = new Map<number, { id: number; label: string; children: unknown[] }>();
    const roots: Array<{ id: number; label: string; children: unknown[] }> = [];
    for (const item of items)
        map.set(item.id, {
            id: item.id,
            label: `${item.categoryName}（${item.categoryCode}）`,
            children: [],
        });
    for (const item of items) {
        const node = map.get(item.id)!;
        const parent = item.parentId == null ? undefined : map.get(item.parentId);
        if (parent) parent.children.push(node);
        else roots.push(node);
    }
    return roots;
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>档案分类</h1>
            <div>
                <el-button @click="openScope">全宗可用分类</el-button
                ><el-button type="primary" @click="openCreateCategory">新建分类</el-button
                ><el-button
                    v-if="selectedCategory?.tableStatus === 'NOT_BUILT'"
                    :loading="saving"
                    @click="buildTable"
                    >生成动态表</el-button
                >
            </div>
        </div>
        <el-row :gutter="16">
            <el-col :xs="24" :lg="7"
                ><el-card v-loading="loading" header="分类树" shadow="never"
                    ><el-select
                        v-model="selectedSchemeId"
                        aria-label="当前方案"
                        placeholder="选择分类方案"
                        style="width: 100%"
                        ><el-option
                            v-for="scheme in schemes"
                            :key="scheme.id"
                            :label="`${scheme.schemeName}（${scheme.schemeCode}）`"
                            :value="scheme.id" /></el-select
                    ><el-text v-if="selectedScheme" type="info">{{
                        selectedScheme.schemeCode
                    }}</el-text
                    ><el-empty
                        v-if="treeData.length === 0"
                        description="暂无分类，请新建"
                        :image-size="48" /><el-tree
                        v-else
                        :data="treeData"
                        node-key="id"
                        default-expand-all
                        highlight-current
                        :current-node-key="selectedCategoryId"
                        @node-click="(node) => (selectedCategoryId = node.id)" /></el-card
            ></el-col>
            <el-col :xs="24" :lg="17"
                ><el-card shadow="never"
                    ><template #header
                        ><div class="card-header">
                            <span>{{
                                selectedCategory
                                    ? `${selectedCategory.categoryName}（${selectedCategory.categoryCode}）`
                                    : "字段定义"
                            }}</span>
                            <div v-if="selectedCategory">
                                <el-text type="info">{{
                                    managementModeLabels[selectedCategory.managementMode]
                                }}</el-text
                                ><el-tag>{{ schemeNameById.get(selectedCategory.schemeId) }}</el-tag
                                ><el-text v-if="selectedCategory.itemTableName" tag="code">{{
                                    selectedCategory.itemTableName
                                }}</el-text
                                ><el-button size="small" @click="openCreateField"
                                    >新建字段</el-button
                                ><el-button size="small" @click="openEditCategory">编辑</el-button
                                ><el-button size="small" type="danger" plain @click="removeCategory"
                                    >删除</el-button
                                >
                            </div>
                        </div></template
                    >
                    <el-table :data="fields" row-key="id"
                        ><el-table-column
                            label="字段编码"
                            prop="fieldCode"
                            width="160"
                        /><el-table-column label="字段名称" prop="fieldName" /><el-table-column
                            label="字段类型"
                            width="100"
                            ><template #default="{ row }">{{
                                fieldTypeLabels[row.fieldType] ?? row.fieldType
                            }}</template></el-table-column
                        ><el-table-column label="层级" width="80"
                            ><template #default="{ row }">{{
                                row.archiveLevel === "VOLUME" ? "案卷" : "条目"
                            }}</template></el-table-column
                        ><el-table-column label="列表显示" width="100"
                            ><template #default="{ row }">{{
                                row.listVisible ? "是" : "否"
                            }}</template></el-table-column
                        ><el-table-column label="启用" width="80"
                            ><template #default="{ row }"
                                ><el-tag :type="row.enabled ? 'success' : 'info'">{{
                                    row.enabled ? "启用" : "停用"
                                }}</el-tag></template
                            ></el-table-column
                        ><el-table-column label="操作" width="130"
                            ><template #default="{ row }"
                                ><el-button link type="primary" @click="openEditField(row)"
                                    >编辑</el-button
                                ><el-button link type="danger" @click="removeField(row)"
                                    >删除</el-button
                                ></template
                            ></el-table-column
                        ></el-table
                    >
                </el-card></el-col
            >
        </el-row>
        <el-dialog
            v-model="categoryDialogOpen"
            :title="categoryMode === 'create' ? '新建分类' : '编辑分类'"
            width="520"
            ><el-form :model="categoryForm" label-position="top"
                ><el-form-item label="分类方案" required
                    ><el-select v-model="categoryForm.schemeId" aria-label="分类方案"
                        ><el-option
                            v-for="scheme in enabledSchemes"
                            :key="scheme.id"
                            :label="scheme.schemeName"
                            :value="scheme.id" /></el-select></el-form-item
                ><el-form-item label="父级分类"
                    ><el-select v-model="categoryForm.parentId" clearable
                        ><el-option
                            v-for="item in parentOptions"
                            :key="item.id"
                            :label="item.categoryName"
                            :value="item.id" /></el-select></el-form-item
                ><el-form-item label="分类编码" required
                    ><el-input v-model="categoryForm.categoryCode" /></el-form-item
                ><el-form-item label="分类名称" required
                    ><el-input v-model="categoryForm.categoryName" /></el-form-item
                ><el-form-item label="管理模式"
                    ><el-select v-model="categoryForm.managementMode"
                        ><el-option
                            v-for="(label, value) in managementModeLabels"
                            :key="value"
                            :label="label"
                            :value="value" /></el-select></el-form-item
                ><el-row :gutter="16"
                    ><el-col :span="12"
                        ><el-form-item label="排序"
                            ><el-input-number
                                v-model="categoryForm.sortOrder"
                                :min="0" /></el-form-item></el-col
                    ><el-col :span="12"
                        ><el-form-item label="启用"
                            ><el-switch
                                v-model="
                                    categoryForm.enabled
                                " /></el-form-item></el-col></el-row></el-form
            ><template #footer
                ><el-button @click="categoryDialogOpen = false">取消</el-button
                ><el-button type="primary" :loading="saving" @click="saveCategory"
                    >确定</el-button
                ></template
            ></el-dialog
        >
        <el-dialog
            v-model="fieldDialogOpen"
            :title="fieldMode === 'create' ? '新建字段' : '编辑字段'"
            width="640"
            ><el-form :model="fieldForm" label-position="top"
                ><el-row :gutter="16"
                    ><el-col :span="12"
                        ><el-form-item label="层级"
                            ><el-select v-model="fieldForm.archiveLevel"
                                ><el-option label="条目" value="ITEM" /><el-option
                                    label="案卷"
                                    value="VOLUME" /></el-select></el-form-item></el-col
                    ><el-col :span="12"
                        ><el-form-item label="字段类型"
                            ><el-select v-model="fieldForm.fieldType"
                                ><el-option
                                    v-for="(label, value) in fieldTypeLabels"
                                    :key="value"
                                    :label="label"
                                    :value="value" /></el-select></el-form-item></el-col></el-row
                ><el-row :gutter="16"
                    ><el-col :span="12"
                        ><el-form-item label="字段编码" required
                            ><el-input v-model="fieldForm.fieldCode" /></el-form-item></el-col
                    ><el-col :span="12"
                        ><el-form-item label="字段名称" required
                            ><el-input
                                v-model="fieldForm.fieldName" /></el-form-item></el-col></el-row
                ><el-form-item v-if="fieldForm.fieldType === 'TEXT'" label="文本长度"
                    ><el-input-number v-model="fieldForm.textLength" :min="1" /></el-form-item
                ><el-row v-if="fieldForm.fieldType === 'DECIMAL'" :gutter="16"
                    ><el-col :span="12"
                        ><el-form-item label="总位数"
                            ><el-input-number
                                v-model="fieldForm.decimalPrecision"
                                :min="1" /></el-form-item></el-col
                    ><el-col :span="12"
                        ><el-form-item label="小数位"
                            ><el-input-number
                                v-model="fieldForm.decimalScale"
                                :min="0" /></el-form-item></el-col></el-row
                ><el-divider>显示与搜索</el-divider>
                <div class="switch-grid">
                    <el-checkbox v-model="fieldForm.listVisible">列表显示</el-checkbox
                    ><el-checkbox v-model="fieldForm.detailVisible">详情显示</el-checkbox
                    ><el-checkbox v-model="fieldForm.editVisible">编辑可见</el-checkbox
                    ><el-checkbox v-model="fieldForm.exactSearchable">精确检索</el-checkbox
                    ><el-checkbox v-model="fieldForm.dataScopeFilterable"
                        >可用于数据范围</el-checkbox
                    ><el-checkbox v-model="fieldForm.enabled">启用</el-checkbox>
                </div></el-form
            ><template #footer
                ><el-button @click="fieldDialogOpen = false">取消</el-button
                ><el-button type="primary" :loading="saving" @click="saveField"
                    >确定</el-button
                ></template
            ></el-dialog
        >
        <el-dialog v-model="scopeDialogOpen" title="全宗可用分类范围" width="720"
            ><el-form label-position="top"
                ><el-form-item label="全宗"
                    ><el-select v-model="scopeFondsCode" @change="loadScopes"
                        ><el-option
                            v-for="item in fonds"
                            :key="item.fondsCode"
                            :label="`${item.fondsName}（${item.fondsCode}）`"
                            :value="item.fondsCode" /></el-select></el-form-item
                ><el-row v-for="(item, index) in scopeItems" :key="index" :gutter="12"
                    ><el-col :span="12"
                        ><el-select v-model="item.categoryId"
                            ><el-option
                                v-for="category in scopeCategoryOptions"
                                :key="category.id"
                                :label="category.categoryName"
                                :value="category.id" /></el-select></el-col
                    ><el-col :span="5"
                        ><el-checkbox v-model="item.defaultFlag">默认</el-checkbox></el-col
                    ><el-col :span="5"><el-input-number v-model="item.sortOrder" :min="0" /></el-col
                    ><el-col :span="2"
                        ><el-button type="danger" plain @click="scopeItems.splice(index, 1)"
                            >删除</el-button
                        ></el-col
                    ></el-row
                ><el-button
                    @click="
                        scopeItems.push({
                            categoryId: 0,
                            defaultFlag: scopeItems.length === 0,
                            sortOrder: scopeItems.length,
                        })
                    "
                    >添加分类</el-button
                ></el-form
            ><template #footer
                ><el-button @click="scopeDialogOpen = false">取消</el-button
                ><el-button type="primary" :loading="saving" @click="saveScopes"
                    >确定</el-button
                ></template
            ></el-dialog
        >
    </section>
</template>

<style scoped>
.card-header,
.card-header > div {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    flex-wrap: wrap;
}
.switch-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
}
</style>
