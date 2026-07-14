import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";

import {
    createArchiveDataScope,
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
import type { ArchiveDataScopeDto } from "@/shared/types/authorization";
import { usePermissionStore } from "@/stores/permissionStore";
import {
    emptyArchiveDataScopeForm,
    toArchiveDataScopeForm,
    toArchiveDataScopeRequest,
} from "./archiveDataScopeForm";
import type { ArchiveDataScopeFormValues } from "./archiveDataScopeForm";

export function useArchiveDataScopes() {
    const permissionStore = usePermissionStore();
    const scopes = ref<ArchiveDataScopeDto[]>([]);
    const fonds = ref<ArchiveFondsDto[]>([]);
    const categories = ref<ArchiveCategoryDto[]>([]);
    const securityLevels = ref<ArchiveSecurityLevelDto[]>([]);
    const retentionPeriods = ref<ArchiveRetentionPeriodDto[]>([]);
    const fieldsByCategory = reactive(new Map<number, ArchiveFieldDto[]>());
    const loadingFieldIds = reactive(new Set<number>());
    const canManageDataScopes = computed(() => permissionStore.has("archive:data-scope:manage"));
    const loading = ref(false);
    const saving = ref(false);
    const open = ref(false);
    const editing = ref<ArchiveDataScopeDto>();
    const formRef = ref<FormInstance>();
    const form = reactive<ArchiveDataScopeFormValues>(emptyArchiveDataScopeForm());
    const rules: FormRules<ArchiveDataScopeFormValues> = {
        scopeCode: [{ required: true, message: "请输入范围编码", trigger: "blur" }],
        scopeName: [{ required: true, message: "请输入范围名称", trigger: "blur" }],
        scopeType: [{ required: true, message: "请选择范围类型", trigger: "change" }],
    };
    const conditional = computed(() => form.scopeType !== "ALL");

    async function loadPage() {
        loading.value = true;
        const results = await Promise.allSettled([
            listArchiveDataScopes(false),
            listArchiveFonds(true),
            listArchiveCategories(true),
            listArchiveSecurityLevels(true),
            listArchiveRetentionPeriods(true),
        ]);
        const [scopeResult, fondsResult, categoryResult, securityResult, retentionResult] = results;
        if (scopeResult?.status === "fulfilled") scopes.value = scopeResult.value.items;
        else ElMessage.error("数据范围加载失败");
        if (fondsResult?.status === "fulfilled") fonds.value = fondsResult.value.items;
        if (categoryResult?.status === "fulfilled") categories.value = categoryResult.value.items;
        if (securityResult?.status === "fulfilled")
            securityLevels.value = securityResult.value.items;
        if (retentionResult?.status === "fulfilled")
            retentionPeriods.value = retentionResult.value.items;
        loading.value = false;
    }

    async function loadFields(categoryId?: number) {
        if (!categoryId || fieldsByCategory.has(categoryId) || loadingFieldIds.has(categoryId))
            return;
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
        Object.assign(form, emptyArchiveDataScopeForm());
        formRef.value?.clearValidate();
        open.value = true;
    }

    function editScope(value: unknown) {
        const row = value as ArchiveDataScopeDto;
        editing.value = row;
        Object.assign(form, toArchiveDataScopeForm(row));
        for (const item of form.dynamicFields) void loadFields(item.categoryId);
        formRef.value?.clearValidate();
        open.value = true;
    }

    function closeDrawer() {
        open.value = false;
        editing.value = undefined;
        Object.assign(form, emptyArchiveDataScopeForm());
        formRef.value?.resetFields();
    }

    function addDynamicField() {
        form.dynamicFields.push({ operator: "EQ", values: [] });
    }

    async function submit() {
        if (!(await formRef.value?.validate().catch(() => false))) return;
        saving.value = true;
        try {
            const request = toArchiveDataScopeRequest(form);
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

    return {
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
    };
}
