import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { defineComponent } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { ArchiveRecordListDto } from "@/shared/types/archive-records";

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
            <button type="button" @click="$emit('submit', { categoryId: 7, keyword: '旧查询', conditions: [], relatedGroups: [] })">提交旧查询</button>
            <button type="button" @click="$emit('submit', { categoryId: 8, keyword: '新查询', conditions: [], relatedGroups: [] })">提交新查询</button>
            <button type="button" @click="$emit('update:model-value', { categoryId: 7, keyword: '未提交', conditions: [], relatedGroups: [] })">修改草稿</button>
            <button type="button" @click="$emit('update:model-value', { categoryId: 8, keyword: '新分类草稿', conditions: [], relatedGroups: [] })">修改分类草稿</button>
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
            .mockResolvedValueOnce({
                fields: [],
                items: [{ id: 2, archive_no: "重试成功" }],
                next: "retry-next",
            });
        renderPage();
        await fireEvent.click(await screen.findByRole("button", { name: "提交查询" }));

        expect(await screen.findByText("查询服务暂不可用")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));
        await waitFor(() => expect(mocks.discoverArchiveRecords).toHaveBeenCalledTimes(2));
        expect(mocks.discoverArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({ categoryId: 7, keyword: "已提交", limit: 100 }),
        );
        expect(await screen.findByText("重试成功")).toBeInTheDocument();
        expect(screen.queryByText("查询服务暂不可用")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "下一页" })).toBeEnabled();
    });

    it("旧查询后成功时不覆盖先完成的新查询结果", async () => {
        const oldRequest = deferred<ArchiveRecordListDto>();
        const latestRequest = deferred<ArchiveRecordListDto>();
        mocks.discoverArchiveRecords
            .mockImplementationOnce(() => oldRequest.promise)
            .mockImplementationOnce(() => latestRequest.promise);
        renderPage();

        await fireEvent.click(await screen.findByRole("button", { name: "提交旧查询" }));
        await fireEvent.click(screen.getByRole("button", { name: "提交新查询" }));
        latestRequest.resolve({ fields: [], items: [{ id: 2, archive_no: "新结果" }] });
        expect(await screen.findByText("新结果")).toBeInTheDocument();
        oldRequest.resolve({ fields: [], items: [{ id: 1, archive_no: "旧结果" }] });
        await oldRequest.promise;

        await waitFor(() => expect(screen.queryByText("旧结果")).not.toBeInTheDocument());
        expect(screen.getByText("新结果")).toBeInTheDocument();
    });

    it("旧查询后失败时不覆盖先完成的新查询结果和错误状态", async () => {
        const oldRequest = deferred<ArchiveRecordListDto>();
        const latestRequest = deferred<ArchiveRecordListDto>();
        mocks.discoverArchiveRecords
            .mockImplementationOnce(() => oldRequest.promise)
            .mockImplementationOnce(() => latestRequest.promise);
        renderPage();

        await fireEvent.click(await screen.findByRole("button", { name: "提交旧查询" }));
        await fireEvent.click(screen.getByRole("button", { name: "提交新查询" }));
        latestRequest.resolve({ fields: [], items: [{ id: 2, archive_no: "新结果" }] });
        expect(await screen.findByText("新结果")).toBeInTheDocument();
        oldRequest.reject(new Error("旧查询失败"));
        await oldRequest.promise.catch(() => undefined);

        await waitFor(() => expect(screen.queryByText("旧查询失败")).not.toBeInTheDocument());
        expect(screen.getByText("新结果")).toBeInTheDocument();
    });

    it("修改分类草稿保留当前结果且翻页仍使用旧分类和关键字", async () => {
        mocks.discoverArchiveRecords.mockResolvedValue({
            fields: [],
            items: [{ id: 1, archive_no: "当前结果" }],
            next: "next-2",
        });
        renderPage();
        await fireEvent.click(await screen.findByRole("button", { name: "修改草稿" }));
        await fireEvent.click(screen.getByRole("button", { name: "提交查询" }));
        expect(await screen.findByText("当前结果")).toBeInTheDocument();

        await fireEvent.click(screen.getByRole("button", { name: "修改分类草稿" }));
        await waitFor(() => expect(mocks.listArchiveFields).toHaveBeenCalledWith(8, "ITEM"));

        expect(screen.getByText("当前结果")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));
        await waitFor(() => expect(mocks.discoverArchiveRecords).toHaveBeenCalledTimes(2));
        expect(mocks.discoverArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({
                categoryId: 7,
                keyword: "已提交",
                cursor: "next-2",
            }),
        );
    });
});

function renderPage() {
    return render(ArchiveLibraryPage, { global: { plugins: [ElementPlus] } });
}

function deferred<T>() {
    let resolve!: (value: T | PromiseLike<T>) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, reject, resolve };
}
