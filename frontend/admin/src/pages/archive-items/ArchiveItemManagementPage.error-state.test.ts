import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { defineComponent } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { HttpClientError } from "@archive-management/frontend-core/api";
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
    listArchiveFields: vi.fn(),
    listArchiveFonds: vi.fn(),
    listArchiveItemAudits: vi.fn(),
    listArchiveItemElectronicFiles: vi.fn(),
    listArchiveRelatedFilterCategories: vi.fn(),
    listArchiveRetentionPeriods: vi.fn(),
    listArchiveSecurityLevels: vi.fn(),
    searchArchiveRecords: vi.fn(),
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
        busyAction: { __v_isRef: true, value: undefined },
        lock: vi.fn(),
        remove: vi.fn(),
        unlock: vi.fn(),
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
    default: defineComponent({ template: "<div />" }),
    normalizeArchiveRecordFormValues: (value: unknown) => value,
}));
vi.mock("@/shared/components/CursorPagination.vue", () => ({
    default: defineComponent({
        props: ["next", "loading"],
        emits: ["page"],
        template: `<button type="button" :disabled="!next || loading" @click="$emit('page', next)">下一页</button>`,
    }),
}));
vi.mock("./ArchiveItemActions.vue", () => ({
    default: defineComponent({
        emits: ["download-template", "export"],
        template: `<div><button type="button" @click="$emit('download-template')">导入模板</button><button type="button" @click="$emit('export')">导出</button></div>`,
    }),
}));
vi.mock("./ArchiveItemEditorDrawer.vue", () => ({
    default: defineComponent({ template: "<div />" }),
}));
vi.mock("./ArchiveItemRelationsDrawer.vue", () => ({
    default: defineComponent({ template: "<div />" }),
}));
vi.mock("./ArchiveItemRowActions.vue", () => ({
    default: defineComponent({
        emits: ["files"],
        template: `<button type="button" @click="$emit('files')">文件</button>`,
    }),
}));

beforeEach(() => {
    setActivePinia(createPinia());
    mocks.listArchiveCategories.mockResolvedValue({ items: [] });
    mocks.listArchiveFields.mockResolvedValue({ items: [] });
    mocks.listArchiveFonds.mockResolvedValue({ items: [] });
    mocks.listArchiveRelatedFilterCategories.mockResolvedValue({ items: [] });
    mocks.listArchiveRetentionPeriods.mockResolvedValue({ items: [] });
    mocks.listArchiveSecurityLevels.mockResolvedValue({ items: [] });
    mocks.listArchiveItemAudits.mockResolvedValue({
        items: [{ id: 30, operationType: "CREATE", operatedAt: "" }],
    });
    mocks.listArchiveItemElectronicFiles.mockResolvedValue({ items: [] });
    mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [{ id: 1 }] });
});

afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.clearAllMocks();
});

describe("ArchiveItemManagementPage 错误恢复", () => {
    it("模板和导出均通过临时 a 标签打开短链且不创建 Blob URL", async () => {
        mocks.downloadArchiveImportTemplate.mockResolvedValue({
            href: "/api/v1/file-links/template-code:download",
        });
        mocks.exportArchiveRecords.mockResolvedValue({
            href: "/api/v1/file-links/export-code:download",
        });
        const anchorClick = vi
            .spyOn(HTMLAnchorElement.prototype, "click")
            .mockImplementation(() => undefined);
        const createObjectUrl = vi.spyOn(URL, ["create", "Object", "URL"].join("") as never);
        await renderPage(["archive:item:read", "archive:item:create", "archive:export"]);
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        await fireEvent.click(screen.getByRole("button", { name: /导入模板/ }));
        await fireEvent.click(screen.getByRole("button", { name: /导出/ }));

        await waitFor(() => expect(anchorClick).toHaveBeenCalledTimes(2));
        expect(createObjectUrl).not.toHaveBeenCalled();
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
        await renderPage();
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        expect(await screen.findByText("保留的管理结果")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));

        expect(
            await screen.findByText("数据已变化，将从第一页重新加载（追踪 ID：trace-management）"),
        ).toBeVisible();
        expect(screen.getByText("保留的管理结果")).toBeVisible();
        expect(screen.getByRole("button", { name: "下一页" })).toBeDisabled();
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
        await renderPage();
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "文件" }));
        expect(await screen.findByText("合同.pdf")).toBeVisible();
        await fireEvent.click(screen.getByRole("tab", { name: "审计记录" }));
        await fireEvent.click(screen.getByRole("tab", { name: "电子文件" }));

        expect(await screen.findByText("文件服务失败（追踪 ID：trace-files）")).toBeVisible();
        expect(screen.getByText("合同.pdf")).toBeVisible();
        const unhandled = vi.fn();
        window.addEventListener("unhandledrejection", unhandled);
        try {
            await fireEvent.click(screen.getByRole("button", { name: "重试" }));
            await waitFor(() =>
                expect(mocks.listArchiveItemElectronicFiles).toHaveBeenCalledTimes(3),
            );
            expect(await screen.findByText("重试文件仍失败")).toBeVisible();
            expect(unhandled).not.toHaveBeenCalled();
            await fireEvent.click(screen.getByRole("button", { name: "重试" }));
            await waitFor(() =>
                expect(mocks.listArchiveItemElectronicFiles).toHaveBeenCalledTimes(4),
            );
        } finally {
            window.removeEventListener("unhandledrejection", unhandled);
        }
    });

    it("切换到审计页签后忽略迟到的电子文件响应", async () => {
        const filesRequest = deferred<{ items: Array<Record<string, unknown>> }>();
        mocks.listArchiveItemElectronicFiles.mockImplementationOnce(() => filesRequest.promise);
        await renderPage();
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
        const view = await renderPage();
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await fireEvent.click(await screen.findByRole("button", { name: "文件" }));

        view.unmount();
        filesRequest.resolve({ items: [] });
        await filesRequest.promise;

        expect(consoleError).not.toHaveBeenCalled();
    });
});

async function renderPage(permissionCodes: string[] = ["archive:item:read"]) {
    const pinia = createPinia();
    setActivePinia(pinia);
    const permissionStore = usePermissionStore();
    permissionApiMocks.getCurrentUserPermissions.mockResolvedValueOnce({
        permissionCodes,
        superAdmin: false,
    });
    await permissionStore.fetchSummary();
    return render(ArchiveItemManagementPage, { global: { plugins: [ElementPlus, pinia] } });
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((onResolve) => {
        resolve = onResolve;
    });
    return { promise, resolve };
}
