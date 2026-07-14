<script setup lang="ts">
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";

import {
    createArchiveDataScope,
    getCurrentUserPermissions,
    listArchiveDataScopeFields,
    listArchiveDataScopes,
    updateArchiveDataScope,
} from "@/shared/api/authorization";
import {
    listArchiveCategories,
    listArchiveFonds,
    listArchiveRetentionPeriods,
    listArchiveSecurityLevels,
} from "@/shared/api/archive-metadata";
import type {
    ArchiveCategoryDto,
    ArchiveFieldDto,
    ArchiveFondsDto,
    ArchiveRetentionPeriodDto,
    ArchiveSecurityLevelDto,
} from "@/shared/types/archive-metadata";
import type {
    ArchiveDataScopeDto,
    ArchiveDataScopeDynamicFieldCondition,
    ArchiveDataScopeRequest,
} from "@/shared/types/authorization";

type DynamicFieldForm = Partial<ArchiveDataScopeDynamicFieldCondition> & { values: string[] };
type ScopeFormValues = Omit<ArchiveDataScopeRequest, "dynamicCondition"> & {
    fondsCodes: string[];
    categoryIds: number[];
    securityLevelIds: number[];
    retentionPeriodIds: number[];
    includeCategoryDescendants: boolean;
    dynamicFields: DynamicFieldForm[];
};

const scopes = ref<ArchiveDataScopeDto[]>([]);
const fonds = ref<ArchiveFondsDto[]>([]);
const categories = ref<ArchiveCategoryDto[]>([]);
const securityLevels = ref<ArchiveSecurityLevelDto[]>([]);
const retentionPeriods = ref<ArchiveRetentionPeriodDto[]>([]);
const fieldsByCategory = reactive(new Map<number, ArchiveFieldDto[]>());
const loadingFieldIds = reactive(new Set<number>());
const canManageDataScopes = ref(false);
const loading = ref(false);
const saving = ref(false);
const open = ref(false);
const editing = ref<ArchiveDataScopeDto>();
const formRef = ref<FormInstance>();
const form = reactive<ScopeFormValues>(emptyForm());
const rules: FormRules<ScopeFormValues> = {
    scopeCode: [{ required: true, message: "请输入范围编码", trigger: "blur" }],
    scopeName: [{ required: true, message: "请输入范围名称", trigger: "blur" }],
    scopeType: [{ required: true, message: "请选择范围类型", trigger: "change" }],
};
const conditional = computed(() => form.scopeType !== "ALL");

async function loadPage() {
    loading.value = true;
    const results = await Promise.allSettled([
        listArchiveDataScopes(false),
        getCurrentUserPermissions(),
        listArchiveFonds(true),
        listArchiveCategories(true),
        listArchiveSecurityLevels(true),
        listArchiveRetentionPeriods(true),
    ]);
    const [
        scopeResult,
        permissionResult,
        fondsResult,
        categoryResult,
        securityResult,
        retentionResult,
    ] = results;
    if (scopeResult?.status === "fulfilled") scopes.value = scopeResult.value.items;
    else ElMessage.error("数据范围加载失败");
    if (permissionResult?.status === "fulfilled") {
        canManageDataScopes.value = permissionResult.value.permissionCodes.includes(
            "archive:data-scope:manage",
        );
    }
    if (fondsResult?.status === "fulfilled") fonds.value = fondsResult.value.items;
    if (categoryResult?.status === "fulfilled") categories.value = categoryResult.value.items;
    if (securityResult?.status === "fulfilled") securityLevels.value = securityResult.value.items;
    if (retentionResult?.status === "fulfilled")
        retentionPeriods.value = retentionResult.value.items;
    loading.value = false;
}

async function loadFields(categoryId?: number) {
    if (!categoryId || fieldsByCategory.has(categoryId) || loadingFieldIds.has(categoryId)) return;
    loadingFieldIds.add(categoryId);
    try {
        fieldsByCategory.set(categoryId, (await listArchiveDataScopeFields(categoryId)).items);
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "字段加载失败");
    } finally {
        loadingFieldIds.delete(categoryId);
    }
}

function createScope() {
    editing.value = undefined;
    Object.assign(form, emptyForm());
    formRef.value?.clearValidate();
    open.value = true;
}

function editScope(value: unknown) {
    const row = value as ArchiveDataScopeDto;
    editing.value = row;
    Object.assign(form, toFormValues(row));
    for (const item of form.dynamicFields) void loadFields(item.categoryId);
    formRef.value?.clearValidate();
    open.value = true;
}

function closeDrawer() {
    open.value = false;
    editing.value = undefined;
    Object.assign(form, emptyForm());
    formRef.value?.resetFields();
}

function addDynamicField() {
    form.dynamicFields.push({ operator: "EQ", values: [] });
}

async function submit() {
    if (!(await formRef.value?.validate().catch(() => false))) return;
    saving.value = true;
    try {
        const request = toRequest(form);
        if (editing.value) await updateArchiveDataScope(editing.value.id, request);
        else await createArchiveDataScope(request);
        ElMessage.success("数据范围已保存");
        closeDrawer();
        scopes.value = (await listArchiveDataScopes(false)).items;
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "保存失败");
    } finally {
        saving.value = false;
    }
}

watch(
    () => form.dynamicFields.map((item) => item.categoryId),
    (categoryIds) => categoryIds.forEach((categoryId) => void loadFields(categoryId)),
);
onMounted(loadPage);
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

<script lang="ts">
type ScopeHelperValues = Omit<ArchiveDataScopeRequest, "dynamicCondition"> & {
    fondsCodes: string[];
    categoryIds: number[];
    securityLevelIds: number[];
    retentionPeriodIds: number[];
    includeCategoryDescendants: boolean;
    dynamicFields: Array<Partial<ArchiveDataScopeDynamicFieldCondition> & { values: string[] }>;
};

function emptyForm(): ScopeHelperValues {
    return {
        scopeCode: "",
        scopeName: "",
        scopeType: "CONDITIONAL",
        dimensions: [],
        enabled: true,
        fondsCodes: [],
        categoryIds: [],
        securityLevelIds: [],
        retentionPeriodIds: [],
        includeCategoryDescendants: false,
        dynamicFields: [],
    };
}

function fieldOptions(fields?: ArchiveFieldDto[]) {
    return (fields ?? [])
        .filter((field) => field.fieldSource !== "BUILTIN")
        .map((field) => ({ label: field.fieldName, value: field.fieldCode }));
}

function toRequest(values: ScopeHelperValues): ArchiveDataScopeRequest {
    if (values.scopeType === "ALL") {
        return {
            scopeCode: values.scopeCode,
            scopeName: values.scopeName,
            scopeType: values.scopeType,
            dimensions: [],
            dynamicCondition: undefined,
            enabled: values.enabled,
            description: values.description,
        };
    }
    return {
        scopeCode: values.scopeCode,
        scopeName: values.scopeName,
        scopeType: values.scopeType,
        dimensions: [
            ...values.fondsCodes.map((targetCode) => ({
                dimensionType: "FONDS" as const,
                targetCode,
                includeDescendants: false,
            })),
            ...values.categoryIds.map((targetId) => ({
                dimensionType: "CATEGORY" as const,
                targetId,
                includeDescendants: values.includeCategoryDescendants,
            })),
            ...values.securityLevelIds.map((targetId) => ({
                dimensionType: "SECURITY_LEVEL" as const,
                targetId,
                includeDescendants: false,
            })),
            ...values.retentionPeriodIds.map((targetId) => ({
                dimensionType: "RETENTION_PERIOD" as const,
                targetId,
                includeDescendants: false,
            })),
        ],
        dynamicCondition:
            values.dynamicFields.length > 0
                ? { dynamicFields: values.dynamicFields as ArchiveDataScopeDynamicFieldCondition[] }
                : undefined,
        enabled: values.enabled,
        description: values.description,
    };
}

function toFormValues(row: ArchiveDataScopeDto): ScopeHelperValues {
    return {
        scopeCode: row.scopeCode,
        scopeName: row.scopeName,
        scopeType: row.scopeType,
        enabled: row.enabled,
        description: row.description,
        dimensions: row.dimensions,
        fondsCodes: row.dimensions
            .filter((item) => item.dimensionType === "FONDS")
            .flatMap((item) => (item.targetCode ? [item.targetCode] : [])),
        categoryIds: row.dimensions
            .filter((item) => item.dimensionType === "CATEGORY")
            .flatMap((item) => (typeof item.targetId === "number" ? [item.targetId] : [])),
        securityLevelIds: row.dimensions
            .filter((item) => item.dimensionType === "SECURITY_LEVEL")
            .flatMap((item) => (typeof item.targetId === "number" ? [item.targetId] : [])),
        retentionPeriodIds: row.dimensions
            .filter((item) => item.dimensionType === "RETENTION_PERIOD")
            .flatMap((item) => (typeof item.targetId === "number" ? [item.targetId] : [])),
        includeCategoryDescendants: row.dimensions.some(
            (item) => item.dimensionType === "CATEGORY" && item.includeDescendants,
        ),
        dynamicFields: row.dynamicCondition?.dynamicFields.map((item) => ({ ...item })) ?? [],
    };
}

function conditionText(value: unknown) {
    const row = value as ArchiveDataScopeDto;
    if (row.scopeType === "ALL") return "*";
    const dimensions = row.dimensions.map((item) => item.dimensionType).join("、");
    const dynamicCount = row.dynamicCondition?.dynamicFields.length ?? 0;
    return [dimensions, dynamicCount > 0 ? `动态字段 ${dynamicCount} 条` : undefined]
        .filter(Boolean)
        .join("；");
}
</script>

<style scoped>
.scope-field {
    display: grid;
    grid-template-columns: 1.2fr 1fr 1fr 1.2fr auto;
    gap: 8px;
    align-items: start;
}
</style>
