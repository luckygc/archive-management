import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { defineComponent } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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
    listArchiveRelatedFilterCategories: vi.fn(),
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
    default: defineComponent({ template: `<div />` }),
    normalizeArchiveRecordFormValues: (value: unknown) => value,
}));

beforeEach(() => {
    setActivePinia(createPinia());
    mocks.listArchiveCategories.mockResolvedValue({ items: [] });
    mocks.listArchiveFonds.mockResolvedValue({ items: [] });
    mocks.listArchiveFields.mockResolvedValue({ items: [] });
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
});
function renderPage(permissionCodes: string[], superAdmin = false) {
    const pinia = createPinia();
    setActivePinia(pinia);
    usePermissionStore().permissionCodes = permissionCodes;
    usePermissionStore().superAdmin = superAdmin;
    return render(ArchiveItemManagementPage, { global: { plugins: [ElementPlus, pinia] } });
}
