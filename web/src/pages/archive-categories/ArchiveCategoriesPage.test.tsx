import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { ArchiveCategoriesPage } from "./ArchiveCategoriesPage";

const archiveApiMocks = vi.hoisted(() => ({
    buildArchiveCategoryTable: vi.fn(),
    createArchiveCategory: vi.fn(),
    createArchiveField: vi.fn(),
    deleteArchiveCategory: vi.fn(),
    deleteArchiveField: vi.fn(),
    listArchiveCategories: vi.fn(),
    listArchiveClassificationSchemes: vi.fn(),
    listArchiveFields: vi.fn(),
    listArchiveFonds: vi.fn(),
    listArchiveFondsCategoryScopes: vi.fn(),
    saveArchiveFondsCategoryScopes: vi.fn(),
    updateArchiveCategory: vi.fn(),
    updateArchiveField: vi.fn(),
}));

vi.mock("@/shared/api/archive", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@/shared/api/archive")>();
    return {
        ...actual,
        ...archiveApiMocks,
    };
});

beforeEach(() => {
    archiveApiMocks.listArchiveClassificationSchemes.mockResolvedValue({
        items: [
            {
                id: 8,
                schemeCode: "default_classification",
                schemeName: "默认分类方案",
                defaultFlag: true,
                enabled: true,
                sortOrder: 0,
                createdAt: "2026-07-07T00:00:00",
                updatedAt: "2026-07-07T00:00:00",
            },
        ],
    });
    archiveApiMocks.listArchiveCategories.mockResolvedValue({
        items: [
            {
                id: 12,
                schemeId: 8,
                categoryCode: "contract",
                categoryName: "合同档案",
                enabled: true,
                managementMode: "ITEM_ONLY",
                sortOrder: 0,
                tableStatus: "NOT_BUILT",
                createdAt: "2026-07-07T00:00:00",
                updatedAt: "2026-07-07T00:00:00",
            },
        ],
    });
    archiveApiMocks.listArchiveFields.mockResolvedValue({ items: [] });
    archiveApiMocks.listArchiveFonds.mockResolvedValue({
        items: [
            {
                id: 1,
                fondsCode: "F001",
                fondsName: "默认全宗",
                enabled: true,
                sortOrder: 0,
                createdAt: "2026-07-07T00:00:00",
                updatedAt: "2026-07-07T00:00:00",
            },
        ],
    });
    archiveApiMocks.listArchiveFondsCategoryScopes.mockResolvedValue({ items: [] });
    archiveApiMocks.saveArchiveFondsCategoryScopes.mockResolvedValue({ items: [] });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ArchiveCategoriesPage", () => {
    it("显示分类方案并在新建分类时选择方案", async () => {
        renderPage();

        expect(await screen.findByText("默认分类方案")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "新建分类" }));

        expect(await screen.findByLabelText("分类方案")).toBeInTheDocument();
    });

    it("提供全宗可用分类范围入口", async () => {
        renderPage();

        fireEvent.click(await screen.findByRole("button", { name: "全宗可用分类" }));

        expect(await screen.findByText("全宗可用分类范围")).toBeInTheDocument();
    });
});

function renderPage() {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
        },
    });
    render(
        <QueryClientProvider client={queryClient}>
            <ArchiveCategoriesPage />
        </QueryClientProvider>,
    );
}
