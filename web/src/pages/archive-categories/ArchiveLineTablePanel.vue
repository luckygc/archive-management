<script setup lang="ts">
import { toRef } from "vue";

import { fieldTypeLabels } from "./useArchiveCategories";
import { useArchiveLineTables } from "./useArchiveLineTables";

const props = defineProps<{ categoryId: number }>();
const {
    buildError,
    building,
    buildTable,
    creatingField,
    creatingTable,
    fieldDialogOpen,
    fieldForm,
    fieldFormError,
    fieldFormRef,
    fieldLoadError,
    fieldRules,
    fields,
    lineFieldErrors,
    loadFields,
    loadingFields,
    loadingTables,
    loadTables,
    openFieldDialog,
    openTableDialog,
    saveField,
    saveTable,
    selectedTable,
    selectTable,
    tableDialogOpen,
    tableFieldErrors,
    tableForm,
    tableFormError,
    tableFormRef,
    tableLoadError,
    tableRules,
    tables,
} = useArchiveLineTables(toRef(props, "categoryId"));
</script>

<template>
    <el-card shadow="never">
        <template #header>
            <div class="panel-header">
                <span>明细表定义</span>
                <el-button
                    size="small"
                    type="primary"
                    :disabled="loadingTables || Boolean(tableLoadError)"
                    @click="openTableDialog"
                >
                    新增明细表
                </el-button>
            </div>
        </template>

        <div v-if="tableLoadError" class="inline-error" role="alert">
            <el-text type="danger">{{ tableLoadError }}</el-text>
            <el-button link type="primary" @click="loadTables()">重试加载</el-button>
        </div>
        <el-table
            v-loading="loadingTables"
            :data="tables"
            row-key="id"
            empty-text="暂无明细表定义"
            highlight-current-row
            @current-change="selectTable"
        >
            <el-table-column label="表编码" prop="tableCode" min-width="150" />
            <el-table-column label="表名称" prop="tableName" min-width="140" />
            <el-table-column
                label="物理表"
                prop="physicalTableName"
                min-width="220"
                show-overflow-tooltip
            />
            <el-table-column label="排序" prop="sortOrder" width="72" />
            <el-table-column label="操作" width="96">
                <template #default="{ row }">
                    <el-button
                        link
                        type="primary"
                        :aria-label="`配置${row.tableName}字段`"
                        @click.stop="selectTable(row)"
                    >
                        配置字段
                    </el-button>
                </template>
            </el-table-column>
        </el-table>

        <template v-if="selectedTable">
            <el-divider />
            <div class="field-header">
                <div>
                    <strong>{{ selectedTable.tableName }}字段</strong>
                    <el-text type="info">{{ selectedTable.tableCode }}</el-text>
                </div>
                <div class="field-actions">
                    <el-button
                        size="small"
                        :disabled="loadingFields || Boolean(fieldLoadError)"
                        @click="openFieldDialog"
                    >
                        新增字段
                    </el-button>
                    <el-button
                        size="small"
                        type="primary"
                        :loading="building"
                        :disabled="fields.length === 0"
                        @click="buildTable"
                    >
                        构建数据表
                    </el-button>
                </div>
            </div>
            <div v-if="buildError" class="inline-error" role="alert">
                <el-text type="danger">{{ buildError }}</el-text>
                <el-button link type="primary" :loading="building" @click="buildTable">
                    重试构建
                </el-button>
            </div>
            <div v-if="fieldLoadError" class="inline-error" role="alert">
                <el-text type="danger">{{ fieldLoadError }}</el-text>
                <el-button link type="primary" @click="loadFields()">重试字段</el-button>
            </div>
            <el-table
                v-loading="loadingFields"
                :data="fields"
                row-key="id"
                empty-text="暂无字段，请先新增字段"
            >
                <el-table-column label="字段编码" prop="fieldCode" min-width="140" />
                <el-table-column label="字段名称" prop="fieldName" min-width="130" />
                <el-table-column label="类型" min-width="90">
                    <template #default="{ row }">
                        {{ fieldTypeLabels[row.fieldType] ?? row.fieldType }}
                    </template>
                </el-table-column>
                <el-table-column label="物理列" prop="columnName" min-width="150" />
                <el-table-column label="精确检索" width="92">
                    <template #default="{ row }">{{ row.exactSearchable ? "是" : "否" }}</template>
                </el-table-column>
                <el-table-column label="排序" prop="sortOrder" width="72" />
            </el-table>
        </template>
    </el-card>

    <el-dialog
        v-model="tableDialogOpen"
        title="新增明细表"
        width="min(520px, calc(100vw - 32px))"
        :close-on-click-modal="!creatingTable"
        :close-on-press-escape="!creatingTable"
        :show-close="!creatingTable"
    >
        <el-form ref="tableFormRef" :model="tableForm" :rules="tableRules" label-position="top">
            <el-form-item label="明细表编码" prop="tableCode" :error="tableFieldErrors.tableCode">
                <el-input v-model="tableForm.tableCode" aria-label="明细表编码" />
            </el-form-item>
            <el-form-item label="明细表名称" prop="tableName" :error="tableFieldErrors.tableName">
                <el-input v-model="tableForm.tableName" aria-label="明细表名称" />
            </el-form-item>
            <el-form-item label="排序" :error="tableFieldErrors.sortOrder">
                <el-input-number v-model="tableForm.sortOrder" :min="0" aria-label="明细表排序" />
            </el-form-item>
            <el-alert v-if="tableFormError" :title="tableFormError" type="error" show-icon />
        </el-form>
        <template #footer>
            <el-button :disabled="creatingTable" @click="tableDialogOpen = false">取消</el-button>
            <el-button type="primary" :loading="creatingTable" @click="saveTable">
                保存明细表
            </el-button>
        </template>
    </el-dialog>

    <el-dialog
        v-model="fieldDialogOpen"
        title="新增明细字段"
        width="min(640px, calc(100vw - 32px))"
        :close-on-click-modal="!creatingField"
        :close-on-press-escape="!creatingField"
        :show-close="!creatingField"
    >
        <el-form ref="fieldFormRef" :model="fieldForm" :rules="fieldRules" label-position="top">
            <el-row :gutter="16">
                <el-col :xs="24" :sm="12">
                    <el-form-item
                        label="字段编码"
                        prop="fieldCode"
                        :error="lineFieldErrors.fieldCode"
                    >
                        <el-input v-model="fieldForm.fieldCode" aria-label="明细字段编码" />
                    </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                    <el-form-item
                        label="字段名称"
                        prop="fieldName"
                        :error="lineFieldErrors.fieldName"
                    >
                        <el-input v-model="fieldForm.fieldName" aria-label="明细字段名称" />
                    </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                    <el-form-item label="字段类型" :error="lineFieldErrors.fieldType">
                        <el-select v-model="fieldForm.fieldType" aria-label="明细字段类型">
                            <el-option
                                v-for="(label, value) in fieldTypeLabels"
                                :key="value"
                                :label="label"
                                :value="value"
                            />
                        </el-select>
                    </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                    <el-form-item
                        label="物理列名"
                        prop="columnName"
                        :error="lineFieldErrors.columnName"
                    >
                        <el-input v-model="fieldForm.columnName" aria-label="物理列名" />
                    </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                    <el-form-item label="排序" :error="lineFieldErrors.sortOrder">
                        <el-input-number
                            v-model="fieldForm.sortOrder"
                            :min="0"
                            aria-label="明细字段排序"
                        />
                    </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                    <el-form-item
                        label="检索"
                        prop="exactSearchable"
                        :error="lineFieldErrors.exactSearchable"
                    >
                        <el-checkbox v-model="fieldForm.exactSearchable">精确检索</el-checkbox>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-alert v-if="fieldFormError" :title="fieldFormError" type="error" show-icon />
        </el-form>
        <template #footer>
            <el-button :disabled="creatingField" @click="fieldDialogOpen = false">取消</el-button>
            <el-button type="primary" :loading="creatingField" @click="saveField">
                保存字段
            </el-button>
        </template>
    </el-dialog>
</template>

<style scoped>
.panel-header,
.field-header,
.field-header > div,
.field-actions,
.inline-error {
    display: flex;
    align-items: center;
    gap: 8px;
}
.panel-header,
.field-header {
    justify-content: space-between;
    flex-wrap: wrap;
}
.field-header > div,
.field-actions {
    flex-wrap: wrap;
}
.inline-error {
    justify-content: space-between;
    margin-bottom: 12px;
    padding: 8px 12px;
    border-radius: var(--el-border-radius-base);
    background: var(--el-color-danger-light-9);
}
</style>
