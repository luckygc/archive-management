import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { computed, onBeforeUnmount, reactive, ref, watch, type Ref } from "vue";

import { errorMessage, HttpClientError } from "@archive-management/frontend-core/api";

import {
    buildArchiveLineTable,
    createArchiveLineField,
    createArchiveLineTable,
    listArchiveLineFields,
    listArchiveLineTables,
} from "@/shared/api/archive-line-tables";
import type {
    ArchiveLineFieldResponse,
    ArchiveLineTableResponse,
} from "@/shared/types/archive-line-tables";
import type { ArchiveFieldType } from "@/shared/types/archive-metadata";

export function useArchiveLineTables(categoryIdRef: Readonly<Ref<number>>) {
    const tables = ref<ArchiveLineTableResponse[]>([]);
    const fields = ref<ArchiveLineFieldResponse[]>([]);
    const selectedTableId = ref<number>();
    const loadingTables = ref(false);
    const loadingFields = ref(false);
    const tableLoadError = ref<string>();
    const fieldLoadError = ref<string>();
    const buildError = ref<string>();
    const tableDialogOpen = ref(false);
    const fieldDialogOpen = ref(false);
    const creatingTable = ref(false);
    const creatingField = ref(false);
    const building = ref(false);
    const tableFormRef = ref<FormInstance>();
    const fieldFormRef = ref<FormInstance>();
    const tableFieldErrors = reactive<Record<string, string>>({});
    const lineFieldErrors = reactive<Record<string, string>>({});
    const tableFormError = ref<string>();
    const fieldFormError = ref<string>();
    const tableForm = reactive({ tableCode: "", tableName: "", sortOrder: 0 });
    const fieldForm = reactive({
        fieldCode: "",
        fieldName: "",
        fieldType: "TEXT" as ArchiveFieldType,
        columnName: "",
        exactSearchable: false,
        sortOrder: 0,
    });
    const tableRules: FormRules = {
        tableCode: identifierRules("请输入明细表编码", "编码须为小写字母、数字或下划线"),
        tableName: [{ required: true, message: "请输入明细表名称", trigger: "blur" }],
    };
    const fieldRules: FormRules = {
        fieldCode: identifierRules("请输入字段编码", "编码须为小写字母、数字或下划线"),
        fieldName: [{ required: true, message: "请输入字段名称", trigger: "blur" }],
        columnName: identifierRules("请输入物理列名", "列名须为小写字母、数字或下划线"),
    };
    const selectedTable = computed(() =>
        tables.value.find((item) => item.id === selectedTableId.value),
    );

    let scopeVersion = 0;
    let tableRequestVersion = 0;
    let fieldRequestVersion = 0;
    let createTableVersion = 0;
    let createFieldVersion = 0;
    let buildVersion = 0;

    watch(
        categoryIdRef,
        (categoryId) => {
            invalidateScope();
            void loadTables(categoryId, scopeVersion);
        },
        { immediate: true },
    );
    watch(selectedTableId, (lineTableId) => {
        fieldRequestVersion += 1;
        createFieldVersion += 1;
        buildVersion += 1;
        fields.value = [];
        fieldLoadError.value = undefined;
        buildError.value = undefined;
        creatingField.value = false;
        building.value = false;
        fieldDialogOpen.value = false;
        clearFormErrors(lineFieldErrors, fieldFormError);
        if (lineTableId != null) void loadFields(lineTableId, scopeVersion);
    });
    onBeforeUnmount(invalidateScope);

    function invalidateScope() {
        scopeVersion += 1;
        tableRequestVersion += 1;
        fieldRequestVersion += 1;
        createTableVersion += 1;
        createFieldVersion += 1;
        buildVersion += 1;
        loadingTables.value = false;
        loadingFields.value = false;
        creatingTable.value = false;
        creatingField.value = false;
        building.value = false;
        tables.value = [];
        fields.value = [];
        selectedTableId.value = undefined;
        tableLoadError.value = undefined;
        fieldLoadError.value = undefined;
        buildError.value = undefined;
        tableDialogOpen.value = false;
        fieldDialogOpen.value = false;
        clearFormErrors(tableFieldErrors, tableFormError);
        clearFormErrors(lineFieldErrors, fieldFormError);
    }

    async function loadTables(categoryId = categoryIdRef.value, expectedScope = scopeVersion) {
        const requestVersion = ++tableRequestVersion;
        loadingTables.value = true;
        tableLoadError.value = undefined;
        try {
            const response = await listArchiveLineTables(categoryId);
            if (
                !isCurrentScope(expectedScope, categoryId) ||
                requestVersion !== tableRequestVersion
            )
                return;
            tables.value = sortByOrder(response.items);
            selectedTableId.value = tables.value.some((item) => item.id === selectedTableId.value)
                ? selectedTableId.value
                : tables.value[0]?.id;
        } catch (error) {
            if (isCurrentScope(expectedScope, categoryId) && requestVersion === tableRequestVersion)
                tableLoadError.value = errorMessage(error, "加载明细表失败");
        } finally {
            if (isCurrentScope(expectedScope, categoryId) && requestVersion === tableRequestVersion)
                loadingTables.value = false;
        }
    }

    async function loadFields(lineTableId = selectedTableId.value, expectedScope = scopeVersion) {
        if (lineTableId == null) return;
        const requestVersion = ++fieldRequestVersion;
        loadingFields.value = true;
        fieldLoadError.value = undefined;
        try {
            const response = await listArchiveLineFields(lineTableId);
            if (!isCurrentTable(expectedScope, lineTableId, requestVersion)) return;
            fields.value = sortByOrder(response.items);
        } catch (error) {
            if (isCurrentTable(expectedScope, lineTableId, requestVersion))
                fieldLoadError.value = errorMessage(error, "加载明细字段失败");
        } finally {
            if (isCurrentTable(expectedScope, lineTableId, requestVersion))
                loadingFields.value = false;
        }
    }

    function openTableDialog() {
        Object.assign(tableForm, { tableCode: "", tableName: "", sortOrder: 0 });
        clearFormErrors(tableFieldErrors, tableFormError);
        tableDialogOpen.value = true;
    }

    async function saveTable() {
        if (creatingTable.value) return;
        const actionVersion = ++createTableVersion;
        const expectedScope = scopeVersion;
        const categoryId = categoryIdRef.value;
        creatingTable.value = true;
        try {
            if (!(await tableFormRef.value?.validate().catch(() => false))) return;
            clearFormErrors(tableFieldErrors, tableFormError);
            const response = await createArchiveLineTable(categoryId, {
                tableCode: tableForm.tableCode.trim(),
                tableName: tableForm.tableName.trim(),
                sortOrder: tableForm.sortOrder,
            });
            if (!isCurrentAction(actionVersion, createTableVersion, expectedScope, categoryId))
                return;
            tables.value = sortByOrder([...tables.value, response]);
            selectedTableId.value = response.id;
            tableDialogOpen.value = false;
            ElMessage.success("明细表已创建");
        } catch (error) {
            if (!isCurrentAction(actionVersion, createTableVersion, expectedScope, categoryId))
                return;
            applyFormErrors(error, tableFieldErrors, tableFormError);
            ElMessage.error(errorMessage(error, "创建明细表失败"));
        } finally {
            if (actionVersion === createTableVersion) creatingTable.value = false;
        }
    }

    function openFieldDialog() {
        Object.assign(fieldForm, {
            fieldCode: "",
            fieldName: "",
            fieldType: "TEXT",
            columnName: "",
            exactSearchable: false,
            sortOrder: 0,
        });
        clearFormErrors(lineFieldErrors, fieldFormError);
        fieldDialogOpen.value = true;
    }

    async function saveField() {
        const lineTableId = selectedTableId.value;
        if (creatingField.value || lineTableId == null) return;
        const actionVersion = ++createFieldVersion;
        const expectedScope = scopeVersion;
        creatingField.value = true;
        try {
            if (!(await fieldFormRef.value?.validate().catch(() => false))) return;
            clearFormErrors(lineFieldErrors, fieldFormError);
            const response = await createArchiveLineField(lineTableId, {
                fieldCode: fieldForm.fieldCode.trim(),
                fieldName: fieldForm.fieldName.trim(),
                fieldType: fieldForm.fieldType,
                columnName: fieldForm.columnName.trim(),
                exactSearchable: fieldForm.exactSearchable,
                sortOrder: fieldForm.sortOrder,
            });
            if (!isCurrentLineAction(actionVersion, createFieldVersion, expectedScope, lineTableId))
                return;
            fields.value = sortByOrder([...fields.value, response]);
            fieldDialogOpen.value = false;
            ElMessage.success("明细字段已创建");
        } catch (error) {
            if (!isCurrentLineAction(actionVersion, createFieldVersion, expectedScope, lineTableId))
                return;
            applyFormErrors(error, lineFieldErrors, fieldFormError);
            ElMessage.error(errorMessage(error, "创建明细字段失败"));
        } finally {
            if (actionVersion === createFieldVersion) creatingField.value = false;
        }
    }

    async function buildTable() {
        const lineTableId = selectedTableId.value;
        if (building.value || lineTableId == null) return;
        const actionVersion = ++buildVersion;
        const expectedScope = scopeVersion;
        building.value = true;
        buildError.value = undefined;
        try {
            const response = await buildArchiveLineTable(lineTableId);
            if (!isCurrentLineAction(actionVersion, buildVersion, expectedScope, lineTableId))
                return;
            tables.value = sortByOrder(
                tables.value.map((item) => (item.id === response.id ? response : item)),
            );
            ElMessage.success("明细数据表已构建");
        } catch (error) {
            if (isCurrentLineAction(actionVersion, buildVersion, expectedScope, lineTableId))
                buildError.value = errorMessage(error, "构建明细数据表失败");
        } finally {
            if (actionVersion === buildVersion) building.value = false;
        }
    }

    function selectTable(value: unknown) {
        const id = (value as Partial<ArchiveLineTableResponse> | null)?.id;
        if (id != null) selectedTableId.value = id;
    }

    function isCurrentScope(expectedScope: number, categoryId: number) {
        return expectedScope === scopeVersion && categoryId === categoryIdRef.value;
    }

    function isCurrentTable(expectedScope: number, lineTableId: number, requestVersion: number) {
        return (
            expectedScope === scopeVersion &&
            lineTableId === selectedTableId.value &&
            requestVersion === fieldRequestVersion
        );
    }

    function isCurrentAction(
        actionVersion: number,
        currentActionVersion: number,
        expectedScope: number,
        categoryId: number,
    ) {
        return actionVersion === currentActionVersion && isCurrentScope(expectedScope, categoryId);
    }

    function isCurrentLineAction(
        actionVersion: number,
        currentActionVersion: number,
        expectedScope: number,
        lineTableId: number,
    ) {
        return (
            actionVersion === currentActionVersion &&
            expectedScope === scopeVersion &&
            lineTableId === selectedTableId.value
        );
    }

    return {
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
    };
}

function identifierRules(requiredMessage: string, patternMessage: string) {
    return [
        { required: true, message: requiredMessage, trigger: "blur" },
        {
            pattern: /^[a-z][a-z0-9_]*$/,
            message: patternMessage,
            trigger: "blur",
        },
    ];
}

function sortByOrder<T extends { id: number; sortOrder: number }>(items: T[]) {
    return [...items].sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id);
}

function clearFormErrors(errors: Record<string, string>, formError: { value?: string }) {
    for (const key of Object.keys(errors)) delete errors[key];
    formError.value = undefined;
}

function applyFormErrors(
    error: unknown,
    errors: Record<string, string>,
    formError: { value?: string },
) {
    if (!(error instanceof HttpClientError)) return;
    const knownFields = new Set([
        "tableCode",
        "tableName",
        "sortOrder",
        "fieldCode",
        "fieldName",
        "fieldType",
        "columnName",
        "exactSearchable",
    ]);
    const unknownMessages: string[] = [];
    for (const violation of error.fieldViolations) {
        if (!violation.field || !violation.message) continue;
        if (knownFields.has(violation.field)) errors[violation.field] = violation.message;
        else unknownMessages.push(violation.message);
    }
    formError.value = [...new Set(unknownMessages)].join("；") || undefined;
}
