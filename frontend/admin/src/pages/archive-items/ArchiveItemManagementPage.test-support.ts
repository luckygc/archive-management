import { cleanup, render } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { defineComponent, h } from "vue";
import { afterEach, beforeEach, vi } from "vitest";

import { usePermissionStore } from "@/stores/permissionStore";
import ArchiveItemManagementPage from "./ArchiveItemManagementPage.vue";

const mocks = vi.hoisted(() => ({
    busyAction: { __v_isRef: true, value: undefined as string | undefined },
    createArchiveRecord: vi.fn(),
    deleteArchiveRecord: vi.fn(),
    downloadArchiveImportTemplate: vi.fn(),
    downloadArchiveItemElectronicFile: vi.fn(),
    exportArchiveRecords: vi.fn(),
    getArchiveRecord: vi.fn(),
    importArchiveRecords: vi.fn(),
    lifecycleLock: vi.fn(),
    lifecycleRemove: vi.fn(),
    lifecycleUnlock: vi.fn(),
    listArchiveCategories: vi.fn(),
    listArchiveFonds: vi.fn(),
    listArchiveFields: vi.fn(),
    listArchiveItemAudits: vi.fn(),
    listArchiveItemElectronicFiles: vi.fn(),
    listArchiveItemRelations: vi.fn(),
    listArchiveRetentionPeriods: vi.fn(),
    listArchiveRelatedFilterCategories: vi.fn(),
    listArchiveSecurityLevels: vi.fn(),
    searchArchiveRecords: vi.fn(),
    createArchiveItemRelation: vi.fn(),
    deleteArchiveItemRelation: vi.fn(),
    discoverArchiveRecords: vi.fn(),
    unbindArchiveItemElectronicFile: vi.fn(),
    updateArchiveRecord: vi.fn(),
    uploadArchiveItemElectronicFile: vi.fn(),
}));
const permissionApiMocks = vi.hoisted(() => ({ getCurrentUserPermissions: vi.fn() }));

vi.mock("@/shared/api/archive-metadata", () => mocks);
vi.mock("@/shared/api/archive-records", () => mocks);
vi.mock("@/shared/api/authorization", () => permissionApiMocks);
vi.mock("./useArchiveItemLifecycle", () => ({
    useArchiveItemLifecycle: () => ({
        busyAction: mocks.busyAction,
        lock: mocks.lifecycleLock,
        remove: mocks.lifecycleRemove,
        unlock: mocks.lifecycleUnlock,
    }),
}));
vi.mock("@/pages/archive-library/ArchiveAdvancedQueryPanel.vue", () => ({
    default: defineComponent({
        emits: ["submit", "update:modelValue"],
        template: `<button type="button" @click="$emit('update:modelValue', { categoryId: 1, conditions: [], relatedGroups: [] }); $emit('submit', { categoryId: 1, conditions: [], relatedGroups: [] })">提交查询</button>`,
    }),
}));
vi.mock("@/pages/archive-library/ArchiveResultTable.vue", () => ({
    default: defineComponent({
        props: ["result"],
        template: `<div><div v-for="row in result.items" :key="row.id"><slot name="actions" :row="row" /></div></div>`,
    }),
}));
vi.mock("@/pages/archive-library/DynamicArchiveFields.vue", () => ({
    default: defineComponent({
        props: ["modelValue", "fields", "disabled", "fieldErrors"],
        emits: ["update:modelValue"],
        setup(props, { emit }) {
            return () =>
                h(
                    "div",
                    (
                        props.fields as Array<{ id: number; fieldCode: string; fieldName: string }>
                    ).map((field) =>
                        h("label", { key: field.id }, [
                            field.fieldName,
                            h("input", {
                                "aria-label": field.fieldName,
                                disabled: props.disabled,
                                value: (props.modelValue as Record<string, unknown>)[
                                    field.fieldCode
                                ],
                                onInput: (event: Event) =>
                                    emit("update:modelValue", {
                                        ...(props.modelValue as Record<string, unknown>),
                                        [field.fieldCode]: (event.target as HTMLInputElement).value,
                                    }),
                            }),
                            (props.fieldErrors as Record<string, string> | undefined)?.[
                                field.fieldCode
                            ]
                                ? h(
                                      "span",
                                      { role: "alert" },
                                      (props.fieldErrors as Record<string, string>)[
                                          field.fieldCode
                                      ],
                                  )
                                : undefined,
                        ]),
                    ),
                );
        },
    }),
    normalizeArchiveRecordFormValues: (value: unknown) => value,
}));

beforeEach(() => {
    setActivePinia(createPinia());
    mocks.listArchiveCategories.mockResolvedValue({ items: [] });
    mocks.listArchiveFonds.mockResolvedValue({ items: [] });
    mocks.listArchiveFields.mockResolvedValue({ items: [] });
    mocks.listArchiveSecurityLevels.mockResolvedValue({
        items: [
            {
                id: 2,
                levelName: "秘密",
                enabled: true,
                sortOrder: 1,
                createdAt: "",
                updatedAt: "",
            },
            {
                id: 20,
                levelName: "绝密（停用）",
                enabled: false,
                sortOrder: 2,
                createdAt: "",
                updatedAt: "",
            },
        ],
    });
    mocks.listArchiveRetentionPeriods.mockResolvedValue({
        items: [
            {
                id: 3,
                periodName: "长期",
                enabled: true,
                sortOrder: 1,
                createdAt: "",
                updatedAt: "",
            },
            {
                id: 30,
                periodName: "永久（停用）",
                enabled: false,
                sortOrder: 2,
                createdAt: "",
                updatedAt: "",
            },
        ],
    });
    mocks.listArchiveRelatedFilterCategories.mockResolvedValue({ items: [] });
    mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 1 }] });
    mocks.busyAction.value = undefined;
    mocks.listArchiveItemElectronicFiles.mockResolvedValue({
        items: [
            {
                id: 10,
                archiveItemId: 1,
                storageObjectId: 20,
                usageType: "DEFAULT",
                displayOrder: 0,
                originalFilename: "合同.pdf",
                fileSize: 1024,
                createdAt: "",
            },
        ],
    });
    mocks.listArchiveItemAudits.mockResolvedValue({
        items: [
            {
                id: 30,
                sourceTableName: "am_archive_item",
                sourceItemId: 1,
                archiveItemId: 1,
                operationType: "CREATE",
                operatedAt: "",
            },
        ],
    });
    mocks.listArchiveItemRelations.mockResolvedValue({
        items: [
            {
                id: 40,
                sourceItemId: 1,
                targetItemId: 2,
                relatedItemId: 2,
                direction: "OUTGOING",
                relatedItem: {
                    itemId: 2,
                    fondsCode: "F001",
                    fondsName: "默认全宗",
                    categoryCode: "contract",
                    categoryName: "合同档案",
                    archiveNo: "REL-002",
                },
            },
        ],
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

export async function renderPage(permissionCodes: string[], superAdmin = false) {
    const pinia = createPinia();
    setActivePinia(pinia);
    const permissionStore = usePermissionStore();
    permissionApiMocks.getCurrentUserPermissions.mockResolvedValueOnce({
        permissionCodes,
        superAdmin,
    });
    await permissionStore.fetchSummary();
    return render(ArchiveItemManagementPage, { global: { plugins: [ElementPlus, pinia] } });
}

export function getMocks() {
    return mocks;
}

const category = {
    id: 1,
    schemeId: 1,
    categoryCode: "contract",
    categoryName: "合同档案",
    managementMode: "ITEM_ONLY" as const,
    enabled: true,
    sortOrder: 0,
    tableStatus: "BUILT" as const,
    createdAt: "",
    updatedAt: "",
};

export function detail(id = 9, title = "建设工程档案") {
    const baseField = {
        archiveLevel: "ITEM" as const,
        categoryId: 1,
        createdAt: "",
        detailColSpan: 1,
        detailSortOrder: 1,
        detailVisible: true,
        editColSpan: 1,
        editControl: "INPUT" as const,
        editSortOrder: 1,
        editVisible: true,
        enabled: true,
        exactSearchable: false,
        dataScopeFilterable: false,
        fieldScope: "METADATA" as const,
        fieldType: "TEXT" as const,
        listSortOrder: 1,
        listVisible: true,
        sortOrder: 1,
        textLength: 200,
        updatedAt: "",
    };
    return {
        item: {
            id,
            fondsCode: "F001",
            fondsName: "默认全宗",
            categoryCode: "contract",
            categoryName: "合同档案",
            archiveNo: "A-001",
            electronicStatus: "DRAFT",
            securityLevelId: 2,
            retentionPeriodId: 3,
            archiveYear: 2026,
            lockedFlag: false,
        },
        category,
        fields: [
            {
                ...baseField,
                id: 11,
                fieldCode: "title",
                fieldName: "题名",
                columnName: "f_title",
            },
        ],
        dynamicFields: { title },
        physicalFields: [
            {
                ...baseField,
                id: 12,
                fieldScope: "PHYSICAL" as const,
                fieldCode: "box_no",
                fieldName: "盒号",
                columnName: "f_box_no",
            },
        ],
        physicalFieldValues: { box_no: "BOX-001" },
    };
}

export function archiveField(
    fieldScope: "METADATA" | "PHYSICAL",
    fieldCode: string,
    fieldName: string,
    id: number,
) {
    return {
        ...detail().fields[0]!,
        id,
        fieldScope,
        fieldCode,
        fieldName,
        columnName: `f_${fieldCode}`,
    };
}

export function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((onResolve, onReject) => {
        resolve = onResolve;
        reject = onReject;
    });
    return { promise, reject, resolve };
}
