import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { defineComponent, h } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { HttpClientError } from "@archive-management/frontend-core/api";
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
    listArchiveRetentionPeriods: vi.fn(),
    listArchiveRelatedFilterCategories: vi.fn(),
    listArchiveSecurityLevels: vi.fn(),
    searchArchiveRecords: vi.fn(),
    unbindArchiveItemElectronicFile: vi.fn(),
    updateArchiveRecord: vi.fn(),
    uploadArchiveItemElectronicFile: vi.fn(),
}));
vi.mock("@/shared/api/archive-metadata", () => mocks);
vi.mock("@/shared/api/archive-records", () => mocks);
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
        emits: ["submit"],
        template: `<button type="button" @click="$emit('submit', { categoryId: 1 })">提交查询</button>`,
    }),
}));
vi.mock("@/pages/archive-library/ArchiveResultTable.vue", () => ({
    default: defineComponent({
        props: ["result"],
        template: `<div><slot name="actions" :row="result.items[0]" /></div>`,
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
});
afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ArchiveItemManagementPage", () => {
    it("无权限时禁用写入入口", async () => {
        renderPage([]);
        expect(await screen.findByRole("button", { name: /导入模板/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /^导入$/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /导出/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /新建档案/ })).toBeDisabled();
    });
    it("查询后按权限打开文件与审计入口", async () => {
        renderPage(
            [
                "archive:item:read",
                "archive:item:create",
                "archive:item:update",
                "archive:item:download-electronic-file",
                "archive:export",
            ],
            true,
        );
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        expect(await screen.findByRole("button", { name: "文件" })).toBeEnabled();
        expect(screen.getByRole("button", { name: "审计" })).toBeEnabled();
        await fireEvent.click(screen.getByRole("button", { name: "文件" }));
        expect(await screen.findByText("合同.pdf")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("tab", { name: "审计记录" }));
        await waitFor(() =>
            expect(mocks.listArchiveItemAudits).toHaveBeenCalledWith({
                archiveItemId: 1,
                limit: 20,
                requestTotal: true,
            }),
        );
        expect(await screen.findByText("CREATE")).toBeInTheDocument();
    });
    it("未锁定档案按权限提供锁定和删除入口", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({
            fields: [],
            items: [{ id: 1, locked_flag: false }],
        });
        renderPage([
            "archive:item:read",
            "archive:item:update",
            "archive:item:lock",
            "archive:item:delete",
        ]);

        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        expect(await screen.findByRole("button", { name: "锁定" })).toBeEnabled();
        expect(screen.queryByRole("button", { name: "解锁" })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "删除" })).toBeEnabled();
        await fireEvent.click(screen.getByRole("button", { name: "锁定" }));
        expect(mocks.lifecycleLock).toHaveBeenCalledWith(1);
    });
    it("已锁定档案提供解锁并禁用编辑和删除", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({
            fields: [],
            items: [{ id: 1, locked_flag: true }],
        });
        renderPage([
            "archive:item:read",
            "archive:item:update",
            "archive:item:lock",
            "archive:item:delete",
        ]);

        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        expect(await screen.findByRole("button", { name: "解锁" })).toBeEnabled();
        expect(screen.queryByRole("button", { name: "锁定" })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "编辑" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "删除" })).toBeDisabled();
        await fireEvent.click(screen.getByRole("button", { name: "解锁" }));
        expect(mocks.lifecycleUnlock).toHaveBeenCalledWith(1);
    });
    it("无权限或动作进行中时禁用生命周期入口", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({
            fields: [],
            items: [{ id: 1, locked_flag: false }],
        });
        mocks.busyAction.value = "lock";
        renderPage(["archive:item:read"]);

        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        expect(await screen.findByRole("button", { name: "锁定" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "删除" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "查看" })).toBeEnabled();
        expect(screen.getByRole("button", { name: "文件" })).toBeEnabled();
    });
    it("查询后使用响应游标翻页", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({
            fields: [],
            items: [{ id: 1 }],
            next: "next-2",
        });
        renderPage(["archive:item:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "下一页" }));

        await waitFor(() =>
            expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith(
                expect.objectContaining({ categoryId: 1, limit: 100, cursor: "next-2" }),
            ),
        );
    });
    it("列表加载失败后原位重试", async () => {
        mocks.searchArchiveRecords
            .mockRejectedValueOnce(new Error("管理列表加载失败"))
            .mockResolvedValueOnce({ fields: [], items: [{ id: 1 }], next: "retry-next" });
        renderPage(["archive:item:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        expect(await screen.findByText("管理列表加载失败")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(2));
        expect(screen.queryByText("管理列表加载失败")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "下一页" })).toBeEnabled();
    });
    it("编辑时加载启用参考数据并分别回填提交实物字段和动态字段", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 9 }] });
        mocks.getArchiveRecord.mockResolvedValue(detail());
        renderPage(["archive:item:read", "archive:item:update"]);

        await waitFor(() => {
            expect(mocks.listArchiveSecurityLevels).toHaveBeenCalledWith(true);
            expect(mocks.listArchiveRetentionPeriods).toHaveBeenCalledWith(true);
        });
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "编辑" }));

        expect((await screen.findAllByText("秘密")).length).toBeGreaterThan(0);
        expect(screen.getAllByText("长期").length).toBeGreaterThan(0);
        expect(screen.queryByText("绝密（停用）")).not.toBeInTheDocument();
        expect(screen.queryByText("永久（停用）")).not.toBeInTheDocument();
        expect(screen.getByLabelText("盒号")).toHaveValue("BOX-001");
        expect(screen.getByLabelText("题名")).toHaveValue("建设工程档案");
        await fireEvent.update(screen.getByLabelText("盒号"), "BOX-009");
        await fireEvent.update(screen.getByLabelText("题名"), "建设工程档案（修订）");
        await fireEvent.click(screen.getByRole("button", { name: "保存" }));

        await waitFor(() =>
            expect(mocks.updateArchiveRecord).toHaveBeenCalledWith(
                9,
                expect.objectContaining({
                    securityLevelId: 2,
                    retentionPeriodId: 3,
                    physicalFields: { box_no: "BOX-009" },
                    dynamicFields: { title: "建设工程档案（修订）" },
                }),
            ),
        );
    });
    it("字段校验失败时回填固定、实物和动态字段错误并保留用户输入", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 9 }] });
        mocks.getArchiveRecord.mockResolvedValue(detail());
        mocks.updateArchiveRecord.mockRejectedValue(
            new HttpClientError(
                "字段校验失败",
                400,
                "INVALID_ARGUMENT",
                [
                    { field: "archiveNo", message: "档号已存在" },
                    // 当前服务端复用动态字段转换器，实物字段可能仍返回 dynamicFields 前缀。
                    { field: "dynamicFields.box_no", message: "盒号格式不合法" },
                    { field: "dynamicFields.title", message: "题名长度不能超过 200" },
                ],
                "trace-task-4",
            ),
        );
        renderPage(["archive:item:read", "archive:item:update"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "编辑" }));
        await fireEvent.update(await screen.findByLabelText("档号"), "A-009");
        await fireEvent.update(screen.getByLabelText("盒号"), "BAD-BOX");
        await fireEvent.update(screen.getByLabelText("题名"), "仍需修订的题名");
        await fireEvent.click(screen.getByRole("button", { name: "保存" }));

        expect(await screen.findByText("档号已存在")).toBeInTheDocument();
        expect(screen.getByText("盒号格式不合法")).toBeInTheDocument();
        expect(screen.getByText("题名长度不能超过 200")).toBeInTheDocument();
        expect(screen.getByLabelText("档号")).toHaveValue("A-009");
        expect(screen.getByLabelText("盒号")).toHaveValue("BAD-BOX");
        expect(screen.getByLabelText("题名")).toHaveValue("仍需修订的题名");
        expect(await screen.findByText(/trace-task-4/)).toBeInTheDocument();
    });
});
function renderPage(permissionCodes: string[], superAdmin = false) {
    const pinia = createPinia();
    setActivePinia(pinia);
    usePermissionStore().permissionCodes = permissionCodes;
    usePermissionStore().superAdmin = superAdmin;
    return render(ArchiveItemManagementPage, { global: { plugins: [ElementPlus, pinia] } });
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

function detail() {
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
        fieldType: "TEXT" as const,
        listSortOrder: 1,
        listVisible: true,
        sortOrder: 1,
        textLength: 200,
        updatedAt: "",
    };
    return {
        item: {
            id: 9,
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
        dynamicFields: { title: "建设工程档案" },
        physicalFields: [
            {
                ...baseField,
                id: 12,
                fieldCode: "box_no",
                fieldName: "盒号",
                columnName: "f_box_no",
            },
        ],
        physicalFieldValues: { box_no: "BOX-001" },
    };
}
