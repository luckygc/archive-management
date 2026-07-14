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
    updateArchiveCategory,
    updateArchiveField,
} from "@/shared/api/archive-metadata";
import type {
    ArchiveCategoryDto,
    ArchiveClassificationSchemeDto,
    ArchiveFieldDto,
    ArchiveFieldType,
    ArchiveFondsDto,
    ArchiveLevel,
    ArchiveManagementMode,
} from "@/shared/types/archive-metadata";
import ArchiveCategoryScopeDialog from "./ArchiveCategoryScopeDialog.vue";

export const managementModeLabels: Record<ArchiveManagementMode, string> = {
    ITEM_ONLY: "仅允许著录条目",
    VOLUME_ITEM: "可建卷并著录条目",
};
export const fieldTypeLabels: Record<string, string> = {
    TEXT: "文本",
    INTEGER: "整数",
    DECIMAL: "小数",
    DATE: "日期",
    DATETIME: "日期时间",
};

export function useArchiveCategories() {
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
    const scopeDialog = ref<InstanceType<typeof ArchiveCategoryScopeDialog>>();
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
            (item) =>
                item.schemeId === categoryForm.schemeId && item.id !== editingCategory.value?.id,
        ),
    );

    async function reloadCategories() {
        categories.value = (await listArchiveCategories()).items;
    }

    function openCreateCategory() {
        categoryMode.value = "create";
        editingCategory.value = undefined;
        Object.assign(categoryForm, {
            schemeId: selectedSchemeId.value ?? enabledSchemes.value[0]?.id,
            parentId: selectedCategoryId.value,
            categoryCode: "",
            categoryName: "",
            managementMode: "ITEM_ONLY",
            enabled: true,
            sortOrder: 0,
        });
        categoryDialogOpen.value = true;
    }

    function openEditCategory() {
        const item = selectedCategory.value;
        if (!item) return;
        categoryMode.value = "edit";
        editingCategory.value = item;
        Object.assign(categoryForm, item);
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
            else
                await updateArchiveField(selectedCategoryId.value, editingField.value!.id, payload);
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
            selectedSchemeId.value = (
                schemes.value.find((item) => item.defaultFlag) ?? schemes.value[0]
            )?.id;
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

    return {
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
    };
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
