import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { defineComponent } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ArchiveLibraryPage from "./ArchiveLibraryPage.vue";

const mocks = vi.hoisted(() => ({
    discoverArchiveRecords: vi.fn(),
    listArchiveCategories: vi.fn(),
    listArchiveFields: vi.fn(),
    listArchiveRelatedFilterCategories: vi.fn(),
}));

vi.mock("@/shared/api/archive-metadata", () => mocks);
vi.mock("@/shared/api/archive-records", () => mocks);
vi.mock("./ArchiveAdvancedQueryPanel.vue", () => ({
    default: defineComponent({
        emits: ["submit", "update:model-value"],
        template: `<div>
            <button type="button" @click="$emit('submit', { categoryId: 7, keyword: '已提交', conditions: [], relatedGroups: [] })">提交查询</button>
            <button type="button" @click="$emit('update:model-value', { categoryId: 7, keyword: '未提交', conditions: [], relatedGroups: [] })">修改草稿</button>
        </div>`,
    }),
}));

beforeEach(() => {
    mocks.listArchiveCategories.mockResolvedValue({ items: [] });
    mocks.listArchiveFields.mockResolvedValue({ items: [] });
    mocks.listArchiveRelatedFilterCategories.mockResolvedValue({ items: [] });
    mocks.discoverArchiveRecords.mockResolvedValue({ fields: [], items: [], next: "next-2" });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ArchiveLibraryPage", () => {
    it("翻页复用已提交查询且不读取未提交草稿", async () => {
        renderPage();
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));
        await waitFor(() => expect(mocks.discoverArchiveRecords).toHaveBeenCalledTimes(1));
        await fireEvent.click(screen.getByRole("button", { name: "修改草稿" }));
        await fireEvent.click(await screen.findByRole("button", { name: "下一页" }));

        await waitFor(() => expect(mocks.discoverArchiveRecords).toHaveBeenCalledTimes(2));
        expect(mocks.discoverArchiveRecords).toHaveBeenLastCalledWith({
            categoryId: 7,
            fondsCode: undefined,
            keyword: "已提交",
            limit: 100,
            relatedGroups: undefined,
            where: undefined,
            orderBy: undefined,
            cursor: "next-2",
        });
    });

    it("查询失败在结果区展示错误并可重试", async () => {
        mocks.discoverArchiveRecords
            .mockRejectedValueOnce(new Error("查询服务暂不可用"))
            .mockResolvedValueOnce({ fields: [], items: [] });
        renderPage();
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        expect(await screen.findByText("查询服务暂不可用")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));
        await waitFor(() => expect(mocks.discoverArchiveRecords).toHaveBeenCalledTimes(2));
        expect(mocks.discoverArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({ categoryId: 7, keyword: "已提交", limit: 100 }),
        );
    });
});

function renderPage() {
    return render(ArchiveLibraryPage, { global: { plugins: [ElementPlus] } });
}
