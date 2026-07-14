<script setup lang="ts">
import {
    archiveDataScopeConditionText as conditionText,
    archiveDataScopeFieldOptions as fieldOptions,
} from "./archiveDataScopeForm";
import { useArchiveDataScopes } from "./useArchiveDataScopes";

const {
    addDynamicField,
    canManageDataScopes,
    categories,
    closeDrawer,
    conditional,
    createScope,
    editScope,
    editing,
    fieldsByCategory,
    fonds,
    form,
    formRef,
    loading,
    loadingFieldIds,
    open,
    retentionPeriods,
    rules,
    saving,
    scopes,
    securityLevels,
    submit,
} = useArchiveDataScopes();
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>数据范围</h1>
            <el-button :disabled="!canManageDataScopes" type="primary" @click="createScope">
                新建范围
            </el-button>
        </div>
        <el-card shadow="never">
            <el-table v-loading="loading" :data="scopes" row-key="id">
                <el-table-column label="范围编码" prop="scopeCode" width="180" />
                <el-table-column label="范围名称" prop="scopeName" />
                <el-table-column label="范围" width="120">
                    <template #default="{ row }">
                        <el-tag :type="row.scopeType === 'ALL' ? 'primary' : 'info'">
                            {{ row.scopeType === "ALL" ? "*" : "条件" }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="条件">
                    <template #default="{ row }">{{ conditionText(row) }}</template>
                </el-table-column>
                <el-table-column label="启用" width="100">
                    <template #default="{ row }">
                        <el-tag :type="row.enabled ? 'success' : 'info'">
                            {{ row.enabled ? "启用" : "停用" }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="100">
                    <template #default="{ row }">
                        <el-button
                            :disabled="!canManageDataScopes"
                            link
                            size="small"
                            type="primary"
                            @click="editScope(row)"
                        >
                            编辑
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <el-drawer
            v-model="open"
            :title="editing ? '编辑数据范围' : '新建数据范围'"
            size="640px"
            destroy-on-close
            @closed="closeDrawer"
        >
            <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
                <el-form-item label="范围编码" prop="scopeCode">
                    <el-input v-model="form.scopeCode" />
                </el-form-item>
                <el-form-item label="范围名称" prop="scopeName">
                    <el-input v-model="form.scopeName" />
                </el-form-item>
                <el-form-item label="范围类型" prop="scopeType">
                    <el-select v-model="form.scopeType">
                        <el-option label="* 任意范围" value="ALL" />
                        <el-option label="条件范围" value="CONDITIONAL" />
                    </el-select>
                </el-form-item>
                <template v-if="conditional">
                    <el-form-item label="全宗范围">
                        <el-select v-model="form.fondsCodes" clearable multiple>
                            <el-option
                                v-for="item in fonds"
                                :key="item.id"
                                :label="`${item.fondsCode} ${item.fondsName}`"
                                :value="item.fondsCode"
                            />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="分类范围">
                        <el-select v-model="form.categoryIds" clearable multiple>
                            <el-option
                                v-for="item in categories"
                                :key="item.id"
                                :label="`${item.categoryCode} ${item.categoryName}`"
                                :value="item.id"
                            />
                        </el-select>
                    </el-form-item>
                    <el-form-item>
                        <el-checkbox v-model="form.includeCategoryDescendants">
                            包含所选分类子级
                        </el-checkbox>
                    </el-form-item>
                    <el-form-item label="密级范围">
                        <el-select v-model="form.securityLevelIds" clearable multiple>
                            <el-option
                                v-for="item in securityLevels"
                                :key="item.id"
                                :label="item.levelName"
                                :value="item.id"
                            />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="保管期限范围">
                        <el-select v-model="form.retentionPeriodIds" clearable multiple>
                            <el-option
                                v-for="item in retentionPeriods"
                                :key="item.id"
                                :label="item.periodName"
                                :value="item.id"
                            />
                        </el-select>
                    </el-form-item>
                    <div
                        v-for="(field, index) in form.dynamicFields"
                        :key="index"
                        class="scope-field"
                    >
                        <el-form-item
                            label="动态分类"
                            :prop="`dynamicFields.${index}.categoryId`"
                            :rules="[{ required: true, message: '请选择分类', trigger: 'change' }]"
                        >
                            <el-select v-model="field.categoryId">
                                <el-option
                                    v-for="item in categories"
                                    :key="item.id"
                                    :label="item.categoryName"
                                    :value="item.id"
                                />
                            </el-select>
                        </el-form-item>
                        <el-form-item
                            label="字段"
                            :prop="`dynamicFields.${index}.fieldCode`"
                            :rules="[{ required: true, message: '请选择字段', trigger: 'change' }]"
                        >
                            <el-select
                                v-model="field.fieldCode"
                                :loading="loadingFieldIds.has(field.categoryId ?? -1)"
                            >
                                <el-option
                                    v-for="option in fieldOptions(
                                        fieldsByCategory.get(field.categoryId ?? -1),
                                    )"
                                    :key="option.value"
                                    :label="option.label"
                                    :value="option.value"
                                />
                            </el-select>
                        </el-form-item>
                        <el-form-item
                            label="操作符"
                            :prop="`dynamicFields.${index}.operator`"
                            :rules="[
                                { required: true, message: '请选择操作符', trigger: 'change' },
                            ]"
                        >
                            <el-select v-model="field.operator">
                                <el-option label="等于" value="EQ" />
                                <el-option label="包含任一" value="IN" />
                                <el-option label="为空" value="IS_NULL" />
                                <el-option label="非空" value="IS_NOT_NULL" />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="值">
                            <el-select v-model="field.values" allow-create filterable multiple />
                        </el-form-item>
                        <el-button @click="form.dynamicFields.splice(index, 1)">删除</el-button>
                    </div>
                    <el-button @click="addDynamicField">添加动态字段条件</el-button>
                </template>
                <el-form-item label="说明" prop="description">
                    <el-input v-model="form.description" :rows="3" type="textarea" />
                </el-form-item>
                <el-form-item>
                    <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="closeDrawer">取消</el-button>
                <el-button
                    :disabled="!canManageDataScopes"
                    :loading="saving"
                    type="primary"
                    @click="submit"
                >
                    保存
                </el-button>
            </template>
        </el-drawer>
    </section>
</template>

<style scoped>
.scope-field {
    display: grid;
    grid-template-columns: 1.2fr 1fr 1fr 1.2fr auto;
    gap: 8px;
    align-items: start;
}
</style>
