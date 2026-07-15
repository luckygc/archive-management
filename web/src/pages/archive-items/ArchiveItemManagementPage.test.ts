import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/vue";
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
        template: `<div><div v-for="row in result.items" :key="row.id"><span>{{ row.archiveNo }}</span><slot name="actions" :row="row" /></div></div>`,
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

describe("ArchiveItemManagementPage", () => {
    it("无权限时禁用写入入口", async () => {
        await renderPage([]);
        expect(await screen.findByRole("button", { name: /导入模板/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /^导入$/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /导出/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /新建档案/ })).toBeDisabled();
    });
    it("查询后按权限打开文件与审计入口", async () => {
        await renderPage(
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
    it("读取权限可打开关系页签且无更新权限时不显示维护入口", async () => {
        await renderPage(["archive:item:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        await fireEvent.click(await screen.findByRole("button", { name: "关系" }));

        expect(await screen.findByText("REL-002")).toBeInTheDocument();
        expect(mocks.listArchiveItemRelations).toHaveBeenCalledWith(1, {
            depth: 1,
            limit: 100,
            cursor: undefined,
        });
        expect(screen.queryByRole("button", { name: "确认关联" })).not.toBeInTheDocument();
    });
    it("只有审计读取权限时不暴露关系页签", async () => {
        await renderPage(["archive:item:audit:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        await fireEvent.click(await screen.findByRole("button", { name: "审计" }));

        expect(await screen.findByText("CREATE")).toBeInTheDocument();
        expect(screen.queryByRole("tab", { name: "档案关系" })).not.toBeInTheDocument();
    });
    it("未锁定档案按权限提供锁定和删除入口", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({
            fields: [],
            items: [{ id: 1, locked_flag: false }],
        });
        await renderPage([
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
        await renderPage([
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
        await renderPage(["archive:item:read"]);

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
        await renderPage(["archive:item:read"]);
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
        await renderPage(["archive:item:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        expect(await screen.findByText("管理列表加载失败")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(2));
        expect(screen.queryByText("管理列表加载失败")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "下一页" })).toBeEnabled();
    });

    it("游标失效时保留旧列表并从原查询第一页重试", async () => {
        mocks.searchArchiveRecords
            .mockResolvedValueOnce({
                fields: [],
                items: [{ id: 1, archiveNo: "保留的管理结果" }],
                next: "next-management",
            })
            .mockRejectedValueOnce(
                new HttpClientError(
                    "游标无效",
                    400,
                    "INVALID_ARGUMENT",
                    [{ field: "cursor", message: "已失效" }],
                    "trace-management",
                ),
            )
            .mockResolvedValueOnce({ fields: [], items: [{ id: 2, archiveNo: "第一页结果" }] });
        await renderPage(["archive:item:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        expect(await screen.findByText("保留的管理结果")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));

        expect(
            await screen.findByText("数据已变化，将从第一页重新加载（追踪 ID：trace-management）"),
        ).toBeVisible();
        expect(screen.getByText("保留的管理结果")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));

        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(3));
        expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({ categoryId: 1, cursor: undefined, limit: 100 }),
        );
    });

    it("电子文件读取失败在抽屉原位展示并保留旧结果重试", async () => {
        mocks.listArchiveItemElectronicFiles
            .mockResolvedValueOnce({
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
            })
            .mockRejectedValueOnce(
                new HttpClientError("文件服务失败", 500, "INTERNAL", [], "trace-files"),
            )
            .mockRejectedValueOnce(new Error("重试文件仍失败"))
            .mockResolvedValueOnce({ items: [] });
        await renderPage(["archive:item:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "文件" }));
        expect(await screen.findByText("合同.pdf")).toBeVisible();
        await fireEvent.click(screen.getByRole("tab", { name: "审计记录" }));
        await fireEvent.click(screen.getByRole("tab", { name: "电子文件" }));

        expect(await screen.findByText("文件服务失败（追踪 ID：trace-files）")).toBeVisible();
        expect(screen.getByText("合同.pdf")).toBeVisible();
        const unhandled = vi.fn();
        window.addEventListener("unhandledrejection", unhandled);
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));

        await waitFor(() => expect(mocks.listArchiveItemElectronicFiles).toHaveBeenCalledTimes(3));
        expect(await screen.findByText("重试文件仍失败")).toBeVisible();
        expect(unhandled).not.toHaveBeenCalled();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));
        await waitFor(() => expect(mocks.listArchiveItemElectronicFiles).toHaveBeenCalledTimes(4));
        window.removeEventListener("unhandledrejection", unhandled);
    });

    it("切换到审计页签后忽略迟到的电子文件响应", async () => {
        const filesRequest = deferred<{ items: Array<Record<string, unknown>> }>();
        mocks.listArchiveItemElectronicFiles.mockImplementationOnce(() => filesRequest.promise);
        await renderPage(["archive:item:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "文件" }));
        await fireEvent.click(screen.getByRole("tab", { name: "审计记录" }));
        expect(await screen.findByText("CREATE")).toBeVisible();

        filesRequest.resolve({
            items: [{ id: 99, originalFilename: "迟到文件.pdf", fileSize: 1 }],
        });
        await filesRequest.promise;

        expect(screen.queryByText("迟到文件.pdf")).not.toBeInTheDocument();
        expect(screen.getByText("CREATE")).toBeVisible();
    });

    it("资源读取期间卸载后忽略迟到响应", async () => {
        const filesRequest = deferred<{ items: Array<Record<string, unknown>> }>();
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
        mocks.listArchiveItemElectronicFiles.mockImplementationOnce(() => filesRequest.promise);
        const view = await renderPage(["archive:item:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "文件" }));

        view.unmount();
        filesRequest.resolve({ items: [] });
        await filesRequest.promise;

        expect(consoleError).not.toHaveBeenCalled();
    });
    it("编辑时加载启用参考数据并分别回填提交实物字段和动态字段", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 9 }] });
        mocks.getArchiveRecord.mockResolvedValue(detail());
        await renderPage(["archive:item:read", "archive:item:update"]);

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
    it("新建时按字段范围分组并分别提交实物字段和动态字段", async () => {
        mocks.listArchiveFonds.mockResolvedValue({
            items: [{ fondsCode: "F001", fondsName: "默认全宗" }],
        });
        mocks.listArchiveFields.mockResolvedValue({
            items: [
                archiveField("METADATA", "title", "题名", 11),
                archiveField("PHYSICAL", "box_no", "盒号", 12),
            ],
        });
        await renderPage(["archive:item:create"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await waitFor(() => expect(mocks.listArchiveFields).toHaveBeenCalledWith(1, "ITEM"));
        await fireEvent.click(screen.getByRole("button", { name: /新建档案/ }));

        const physicalGroup = screen.getByRole("heading", { name: "实物字段" }).nextElementSibling!;
        const dynamicGroup = screen.getByRole("heading", { name: "动态字段" }).nextElementSibling!;
        expect(within(physicalGroup as HTMLElement).getByLabelText("盒号")).toBeInTheDocument();
        expect(
            within(physicalGroup as HTMLElement).queryByLabelText("题名"),
        ).not.toBeInTheDocument();
        expect(within(dynamicGroup as HTMLElement).getByLabelText("题名")).toBeInTheDocument();
        expect(
            within(dynamicGroup as HTMLElement).queryByLabelText("盒号"),
        ).not.toBeInTheDocument();

        await fireEvent.click(screen.getByLabelText("全宗"));
        await fireEvent.click(await screen.findByText("F001 默认全宗"));
        await fireEvent.update(screen.getByLabelText("盒号"), "BOX-001");
        await fireEvent.update(screen.getByLabelText("题名"), "建设工程档案");
        await fireEvent.click(screen.getByRole("button", { name: "保存" }));

        await waitFor(() =>
            expect(mocks.createArchiveRecord).toHaveBeenCalledWith(
                expect.objectContaining({
                    categoryId: 1,
                    physicalFields: { box_no: "BOX-001" },
                    dynamicFields: { title: "建设工程档案" },
                }),
            ),
        );
    });
    it("连续打开两条档案时旧请求失败不关闭新抽屉", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 1 }, { id: 2 }] });
        const first = deferred<ReturnType<typeof detail>>();
        const second = deferred<ReturnType<typeof detail>>();
        mocks.getArchiveRecord.mockImplementationOnce(() => first.promise);
        mocks.getArchiveRecord.mockImplementationOnce(() => second.promise);
        await renderPage(["archive:item:read", "archive:item:update"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        const editButtons = await screen.findAllByRole("button", { name: "编辑" });
        await fireEvent.click(editButtons[0]!);
        await fireEvent.click(editButtons[1]!);

        first.reject(new Error("旧请求失败"));
        await waitFor(() => expect(screen.getByText("编辑档案 2")).toBeInTheDocument());
        second.resolve(detail(2, "第二条档案"));
        expect(await screen.findByDisplayValue("第二条档案")).toBeInTheDocument();
    });
    it("旧请求成功时不提前结束新请求加载状态", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 1 }, { id: 2 }] });
        const first = deferred<ReturnType<typeof detail>>();
        const second = deferred<ReturnType<typeof detail>>();
        mocks.getArchiveRecord.mockImplementationOnce(() => first.promise);
        mocks.getArchiveRecord.mockImplementationOnce(() => second.promise);
        await renderPage(["archive:item:read", "archive:item:update"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        const editButtons = await screen.findAllByRole("button", { name: "编辑" });
        await fireEvent.click(editButtons[0]!);
        await fireEvent.click(editButtons[1]!);

        first.resolve(detail(1, "第一条档案"));
        await waitFor(() => expect(document.querySelector(".el-loading-mask")).not.toBeNull());
        second.resolve(detail(2, "第二条档案"));
        expect(await screen.findByDisplayValue("第二条档案")).toBeInTheDocument();
    });
    it("同一档案重复打开时旧成功结果不覆盖新结果", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 1 }] });
        const first = deferred<ReturnType<typeof detail>>();
        const second = deferred<ReturnType<typeof detail>>();
        mocks.getArchiveRecord.mockImplementationOnce(() => first.promise);
        mocks.getArchiveRecord.mockImplementationOnce(() => second.promise);
        await renderPage(["archive:item:read", "archive:item:update"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        const editButton = await screen.findByRole("button", { name: "编辑" });
        await fireEvent.click(editButton);
        await fireEvent.click(editButton);

        second.resolve(detail(1, "最新结果"));
        expect(await screen.findByDisplayValue("最新结果")).toBeInTheDocument();
        first.resolve(detail(1, "过期结果"));
        await waitFor(() => expect(screen.getByLabelText("题名")).toHaveValue("最新结果"));
    });
    it("关闭抽屉后旧成功结果不再回填", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 1 }] });
        const pending = deferred<ReturnType<typeof detail>>();
        mocks.getArchiveRecord.mockReturnValue(pending.promise);
        await renderPage(["archive:item:read", "archive:item:update"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "编辑" }));
        await fireEvent.click(screen.getByRole("button", { name: "取消" }));
        pending.resolve(detail(1, "不应回填"));

        await waitFor(() => expect(screen.queryByDisplayValue("不应回填")).not.toBeInTheDocument());
        expect(screen.queryByText("编辑档案 1")).not.toBeInTheDocument();
    });
    it("打开新建抽屉后旧详情失败不关闭新抽屉", async () => {
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 1 }] });
        const pending = deferred<ReturnType<typeof detail>>();
        mocks.getArchiveRecord.mockReturnValue(pending.promise);
        await renderPage(["archive:item:read", "archive:item:create", "archive:item:update"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "编辑" }));
        await fireEvent.click(screen.getByRole("button", { name: /新建档案/ }));
        pending.reject(new Error("过期详情失败"));

        await waitFor(() =>
            expect(screen.getByRole("button", { name: "保存" })).toBeInTheDocument(),
        );
        expect(screen.queryByText("编辑档案 1")).not.toBeInTheDocument();
    });
    it("参考数据独立加载失败时保留成功项并支持原位重试", async () => {
        mocks.listArchiveSecurityLevels.mockRejectedValueOnce(new Error("密级服务不可用"));
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 9 }] });
        mocks.getArchiveRecord.mockResolvedValue(detail());
        await renderPage(["archive:item:read", "archive:item:update"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "编辑" }));

        expect((await screen.findAllByText("长期")).length).toBeGreaterThan(0);
        expect(await screen.findByText(/密级服务不可用/)).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试参考数据" }));
        await waitFor(() => expect(mocks.listArchiveSecurityLevels).toHaveBeenCalledTimes(2));
        expect(screen.queryByText(/密级服务不可用/)).not.toBeInTheDocument();
        expect((await screen.findAllByText("秘密")).length).toBeGreaterThan(0);
    });
    it("参考数据加载期间不把当前 ID 标记为不可用", async () => {
        const security = deferred<{ items: never[] }>();
        const retention = deferred<{ items: never[] }>();
        mocks.listArchiveSecurityLevels.mockReturnValue(security.promise);
        mocks.listArchiveRetentionPeriods.mockReturnValue(retention.promise);
        mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 9 }] });
        mocks.getArchiveRecord.mockResolvedValue(detail());
        await renderPage(["archive:item:read", "archive:item:update"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "编辑" }));
        await screen.findByLabelText("题名");

        expect(screen.queryByText(/当前密级 ID/)).not.toBeInTheDocument();
        expect(screen.queryByText(/当前保管期限 ID/)).not.toBeInTheDocument();
        security.resolve({ items: [] });
        retention.resolve({ items: [] });
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
                    { field: "physicalFields.box_no", message: "盒号格式不合法" },
                    { field: "dynamicFields.title", message: "题名长度不能超过 200" },
                    { field: "dynamicFields.unknown", message: "未知字段错误" },
                ],
                "trace-task-4",
            ),
        );
        await renderPage(["archive:item:read", "archive:item:update"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "编辑" }));
        await fireEvent.update(await screen.findByLabelText("档号"), "A-009");
        await fireEvent.update(screen.getByLabelText("盒号"), "BAD-BOX");
        await fireEvent.update(screen.getByLabelText("题名"), "仍需修订的题名");
        await fireEvent.click(screen.getByRole("button", { name: "保存" }));

        expect(await screen.findByText("档号已存在")).toBeInTheDocument();
        expect(screen.getByText("盒号格式不合法")).toBeInTheDocument();
        expect(screen.getByText("题名长度不能超过 200")).toBeInTheDocument();
        expect(screen.getByText("未知字段错误")).toBeInTheDocument();
        expect(screen.getByLabelText("档号")).toHaveValue("A-009");
        expect(screen.getByLabelText("盒号")).toHaveValue("BAD-BOX");
        expect(screen.getByLabelText("题名")).toHaveValue("仍需修订的题名");
        expect(await screen.findByText(/trace-task-4/)).toBeInTheDocument();
    });
});
async function renderPage(permissionCodes: string[], superAdmin = false) {
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

function detail(id = 9, title = "建设工程档案") {
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

function archiveField(
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

function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((onResolve, onReject) => {
        resolve = onResolve;
        reject = onReject;
    });
    return { promise, reject, resolve };
}
