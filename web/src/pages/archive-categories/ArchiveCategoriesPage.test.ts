import { cleanup, fireEvent, render, screen } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ArchiveCategoriesPage from "./ArchiveCategoriesPage.vue";

const mocks = vi.hoisted(() => ({
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
vi.mock("@/shared/api/archive-metadata", () => mocks);
beforeEach(() => {
    mocks.listArchiveClassificationSchemes.mockResolvedValue({
        items: [
            {
                id: 8,
                schemeCode: "default_classification",
                schemeName: "默认分类方案",
                defaultFlag: true,
                enabled: true,
                sortOrder: 0,
                createdAt: "",
                updatedAt: "",
            },
        ],
    });
    mocks.listArchiveCategories.mockResolvedValue({
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
                createdAt: "",
                updatedAt: "",
            },
        ],
    });
    mocks.listArchiveFields.mockResolvedValue({ items: [] });
    mocks.listArchiveFonds.mockResolvedValue({
        items: [
            {
                id: 1,
                fondsCode: "F001",
                fondsName: "默认全宗",
                enabled: true,
                sortOrder: 0,
                createdAt: "",
                updatedAt: "",
            },
        ],
    });
    mocks.listArchiveFondsCategoryScopes.mockResolvedValue({ items: [] });
});
afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});
describe("ArchiveCategoriesPage", () => {
    it("显示分类方案并在新建分类时选择方案", async () => {
        renderPage();
        expect((await screen.findAllByText(/默认分类方案/)).length).toBeGreaterThan(0);
        await fireEvent.click(screen.getByRole("button", { name: "新建分类" }));
        expect(
            (await screen.findAllByLabelText("分类方案")).some(
                (item) => item.getAttribute("role") === "combobox",
            ),
        ).toBe(true);
    });
    it("提供全宗可用分类范围入口", async () => {
        renderPage();
        await fireEvent.click(await screen.findByRole("button", { name: "全宗可用分类" }));
        expect(await screen.findByText("全宗可用分类范围")).toBeInTheDocument();
    });
});
function renderPage() {
    return render(ArchiveCategoriesPage, { global: { plugins: [ElementPlus] } });
}
