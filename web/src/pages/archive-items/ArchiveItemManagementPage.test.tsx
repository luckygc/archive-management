import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { ArchiveItemManagementPage } from "./ArchiveItemManagementPage";

const mocks = vi.hoisted(() => ({
    bindArchiveItemElectronicFile: vi.fn(),
    downloadArchiveImportTemplate: vi.fn(),
    downloadArchiveItemElectronicFile: vi.fn(),
    exportArchiveRecords: vi.fn(),
    getCurrentUserPermissions: vi.fn(),
    importArchiveRecords: vi.fn(),
    listArchiveCategories: vi.fn(),
    listArchiveFields: vi.fn(),
    listArchiveItemAudits: vi.fn(),
    listArchiveItemElectronicFiles: vi.fn(),
    listArchiveRelatedFilterCategories: vi.fn(),
    searchArchiveRecords: vi.fn(),
    unbindArchiveItemElectronicFile: vi.fn(),
}));

vi.mock("@/shared/api/archive", () => mocks);

vi.mock("@/pages/archive-library/ArchiveAdvancedQueryPanel", () => ({
    ArchiveAdvancedQueryPanel: ({ onSubmit }: { onSubmit: (values: unknown) => void }) => (
        <button type="button" onClick={() => onSubmit({ categoryId: 1 })}>
            提交查询
        </button>
    ),
}));

vi.mock("@/pages/archive-library/ArchiveResultTable", () => ({
    ArchiveResultTable: ({
        actionColumn,
    }: {
        actionColumn?: (row: Record<string, unknown>) => React.ReactNode;
    }) => <div>{actionColumn?.({ id: 1 })}</div>,
}));

beforeEach(() => {
    mocks.listArchiveCategories.mockResolvedValue({ items: [] });
    mocks.listArchiveFields.mockResolvedValue({ items: [] });
    mocks.listArchiveRelatedFilterCategories.mockResolvedValue({ items: [] });
    mocks.searchArchiveRecords.mockResolvedValue({
        fields: [],
        items: [{ id: 1 }],
        tableBuilt: true,
    });
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
                createdAt: "2026-06-30T00:00:00",
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
                operatedAt: "2026-06-30T00:00:00",
            },
        ],
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ArchiveItemManagementPage", () => {
    it("disables mutation entries when current user has no matching permissions", async () => {
        mocks.getCurrentUserPermissions.mockResolvedValue({ permissionCodes: [] });

        renderPage();

        expect(await screen.findByRole("button", { name: /导入模板/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /^upload.*导入$/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /导出/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /新建档案/ })).toBeDisabled();
    });

    it("opens file and audit entries after querying records with permissions", async () => {
        mocks.getCurrentUserPermissions.mockResolvedValue({
            permissionCodes: [
                "archive:item:read",
                "archive:item:create",
                "archive:item:update",
                "archive:file:bind",
                "archive:file:download",
                "archive:export",
            ],
        });

        renderPage();

        fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        expect(await screen.findByRole("button", { name: /文件/ })).toBeEnabled();
        expect(screen.getByRole("button", { name: /审计/ })).toBeEnabled();

        fireEvent.click(screen.getByRole("button", { name: /文件/ }));
        expect(await screen.findByText("合同.pdf")).toBeTruthy();
        expect(screen.getByRole("button", { name: /绑定/ })).toBeEnabled();

        fireEvent.click(screen.getByRole("tab", { name: "审计记录" }));
        await waitFor(() => {
            expect(mocks.listArchiveItemAudits).toHaveBeenCalledWith({
                archiveItemId: 1,
                limit: 20,
                requestTotal: true,
            });
        });
        expect(await screen.findByText("CREATE")).toBeTruthy();
    });
});

function renderPage() {
    render(
        <QueryClientProvider client={new QueryClient()}>
            <ArchiveItemManagementPage />
        </QueryClientProvider>,
    );
}
