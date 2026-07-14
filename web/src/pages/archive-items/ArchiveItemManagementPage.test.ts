import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { defineComponent } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissionStore } from "@/stores/permissionStore";
import ArchiveItemManagementPage from "./ArchiveItemManagementPage.vue";

const mocks = vi.hoisted(() => ({
    createArchiveRecord: vi.fn(),
    downloadArchiveImportTemplate: vi.fn(),
    downloadArchiveItemElectronicFile: vi.fn(),
    exportArchiveRecords: vi.fn(),
    getArchiveRecord: vi.fn(),
    importArchiveRecords: vi.fn(),
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
vi.mock("@/pages/archive-library/ArchiveAdvancedQueryPanel.vue", () => ({
    default: defineComponent({
        emits: ["submit"],
        template: `<button type="button" @click="$emit('submit', { categoryId: 1 })">提交查询</button>`,
    }),
}));
vi.mock("@/pages/archive-library/ArchiveResultTable.vue", () => ({
    default: defineComponent({
        props: ["result"],
        template: `<div><slot name="actions" :row="{ id: 1 }" /></div>`,
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
            .mockResolvedValueOnce({ fields: [], items: [{ id: 1 }] });
        renderPage(["archive:item:read"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        expect(await screen.findByText("管理列表加载失败")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(2));
    });
});
function renderPage(permissionCodes: string[], superAdmin = false) {
    const pinia = createPinia();
    setActivePinia(pinia);
    usePermissionStore().permissionCodes = permissionCodes;
    usePermissionStore().superAdmin = superAdmin;
    return render(ArchiveItemManagementPage, { global: { plugins: [ElementPlus, pinia] } });
}
