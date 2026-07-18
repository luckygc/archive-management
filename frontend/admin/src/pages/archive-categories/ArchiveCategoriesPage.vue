<script setup lang="ts">
import { computed } from "vue";

import { usePermissionStore } from "@/stores/permissionStore";
import ArchiveCategoryScopeDialog from "./ArchiveCategoryScopeDialog.vue";
import ArchiveLineTablePanel from "./ArchiveLineTablePanel.vue";
import {
    fieldTypeLabels,
    managementModeLabels,
    useArchiveCategories,
} from "./useArchiveCategories";

const {
    buildTable,
    categories,
    categoryDialogOpen,
    categoryForm,
    categoryMode,
    enabledSchemes,
    fieldDialogOpen,
    fieldForm,
    fieldMode,
    fields,
    fonds,
    loading,
    openCreateCategory,
    openCreateField,
    openEditCategory,
    openEditField,
    parentOptions,
    removeCategory,
    removeField,
    saveCategory,
    saveField,
    saving,
    schemeNameById,
    schemes,
    scopeDialog,
    selectedCategory,
    selectedCategoryId,
    selectedScheme,
    selectedSchemeId,
    treeData,
} = useArchiveCategories();

const permissionStore = usePermissionStore();
const canManageMetadata = computed(() => permissionStore.has("archive:metadata:manage"));
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>档案分类</h1>
            <div>
                <el-button :disabled="loading || fonds.length === 0" @click="scopeDialog?.show()"
                    >全宗可用分类</el-button
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
                </el-card>
                <div v-if="canManageMetadata && selectedCategoryId" class="line-table-panel">
                    <ArchiveLineTablePanel :category-id="selectedCategoryId" />
                </div>
            </el-col>
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
        <ArchiveCategoryScopeDialog ref="scopeDialog" :fonds="fonds" :categories="categories" />
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
.line-table-panel {
    margin-top: 16px;
}
</style>
