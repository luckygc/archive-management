import { cleanup, render, waitFor } from "@testing-library/vue";
import { defineComponent, h } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { HttpClientError } from "@archive-management/frontend-core/api";
import type { ArchiveRecordListDto } from "@/shared/types/archive-records";

import { useArchiveItemSearch } from "./useArchiveItemSearch";

const mocks = vi.hoisted(() => ({
    listArchiveCategories: vi.fn(),
    listArchiveFields: vi.fn(),
    listArchiveFonds: vi.fn(),
    listArchiveRelatedFilterCategories: vi.fn(),
    searchArchiveRecords: vi.fn(),
}));

vi.mock("@/shared/api/archive-metadata", () => mocks);
vi.mock("@/shared/api/archive-records", () => mocks);

beforeEach(() => {
    mocks.listArchiveCategories.mockResolvedValue({ items: [] });
    mocks.listArchiveFields.mockResolvedValue({ items: [] });
    mocks.listArchiveFonds.mockResolvedValue({ items: [] });
    mocks.listArchiveRelatedFilterCategories.mockResolvedValue({ items: [] });
    mocks.searchArchiveRecords.mockResolvedValue({
        fields: [],
        items: [],
        next: "next-2",
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("useArchiveItemSearch", () => {
    it("翻页只改变游标并复用已提交查询和排序", async () => {
        const search = renderSearch();
        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(1));

        search.queryForm.fondsCode = "未提交草稿";
        search.orderResults([{ field: "archiveYear", direction: "DESC" }]);
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(2));

        search.page("next-2");
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(3));

        expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith({
            categoryId: 7,
            fondsCode: undefined,
            keyword: undefined,
            limit: 100,
            relatedGroups: undefined,
            where: undefined,
            orderBy: [{ field: "archiveYear", direction: "DESC" }],
            cursor: "next-2",
        });
    });

    it("修改页大小从已提交查询第一页重新加载", async () => {
        const search = renderSearch();
        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(1));

        search.page("next-2");
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(2));
        search.limitChange(200);
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(3));

        expect(search.limit.value).toBe(200);
        expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({ categoryId: 7, limit: 200, cursor: undefined }),
        );
    });

    it("加载失败保留结果并按相同游标重试", async () => {
        const search = renderSearch();
        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        await waitFor(() => expect(search.result.value?.next).toBe("next-2"));

        mocks.searchArchiveRecords.mockRejectedValueOnce(new Error("查询服务暂不可用"));
        search.page("next-2");
        await waitFor(() => expect(search.loadError.value).toBe("查询服务暂不可用"));

        expect(search.result.value?.next).toBe("next-2");
        void search.refresh();
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(3));
        expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({ categoryId: 7, limit: 100, cursor: "next-2" }),
        );
    });

    it("游标字段错误保留结果和草稿并从相同已提交查询第一页重试", async () => {
        const search = renderSearch();
        mocks.searchArchiveRecords.mockResolvedValueOnce({
            fields: [],
            items: [{ id: 1 }],
            self: "self-2",
            prev: "prev-2",
            next: "next-2",
            first: "first-2",
        });
        search.submit({
            categoryId: 7,
            fondsCode: "F-COMMITTED",
            conditions: [],
            relatedGroups: [],
        });
        await waitFor(() => expect(search.result.value?.next).toBe("next-2"));
        search.limit.value = 200;
        search.orderBy.value = [{ field: "archiveYear", direction: "DESC" }];
        mocks.searchArchiveRecords.mockRejectedValueOnce(
            new HttpClientError(
                "游标无效",
                400,
                "INVALID_ARGUMENT",
                [{ field: "cursor", message: "游标已过期" }],
                "trace-cursor",
            ),
        );

        search.page("next-2");
        await waitFor(() =>
            expect(search.loadError.value).toBe(
                "数据已变化，将从第一页重新加载（追踪 ID：trace-cursor）",
            ),
        );
        search.queryForm.fondsCode = "未提交草稿";

        expect(search.result.value?.items).toEqual([{ id: 1 }]);
        expect(search.result.value).toEqual(
            expect.objectContaining({
                self: undefined,
                prev: undefined,
                next: undefined,
                first: undefined,
            }),
        );
        expect(search.limit.value).toBe(200);
        expect(search.orderBy.value).toEqual([{ field: "archiveYear", direction: "DESC" }]);
        expect(search.committedQuery.value?.fondsCode).toBe("F-COMMITTED");
        await search.refresh();
        expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({
                categoryId: 7,
                fondsCode: "F-COMMITTED",
                orderBy: [{ field: "archiveYear", direction: "DESC" }],
                limit: 200,
                cursor: undefined,
            }),
        );
        expect(search.queryForm.fondsCode).toBe("未提交草稿");
    });

    it("普通错误展示追踪 ID 且重试继续使用失败时的游标", async () => {
        const search = renderSearch();
        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        await waitFor(() => expect(search.result.value?.next).toBe("next-2"));
        mocks.searchArchiveRecords.mockRejectedValueOnce(
            new HttpClientError("服务繁忙", 500, "INTERNAL", [], "trace-normal"),
        );

        search.page("next-2");
        await waitFor(() =>
            expect(search.loadError.value).toBe("服务繁忙（追踪 ID：trace-normal）"),
        );
        await search.refresh();

        expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({ cursor: "next-2" }),
        );
    });

    it("重试进行中重复触发只复用同一请求", async () => {
        const retryRequest = deferred<ArchiveRecordListDto>();
        const search = renderSearch();
        mocks.searchArchiveRecords.mockRejectedValueOnce(new Error("暂不可用"));
        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        await waitFor(() => expect(search.loadError.value).toBe("暂不可用"));
        mocks.searchArchiveRecords.mockImplementationOnce(() => retryRequest.promise);

        const first = search.refresh();
        const second = search.refresh();

        expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(2);
        expect(second).toBe(first);
        retryRequest.resolve({ fields: [], items: [] });
        await first;
    });

    it("重试再次失败时更新原位错误且不产生未处理拒绝", async () => {
        const unhandled = vi.fn();
        window.addEventListener("unhandledrejection", unhandled);
        const search = renderSearch();
        mocks.searchArchiveRecords.mockRejectedValueOnce(new Error("首次失败"));
        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        await waitFor(() => expect(search.loadError.value).toBe("首次失败"));
        mocks.searchArchiveRecords.mockRejectedValueOnce(new Error("重试仍失败"));

        await expect(search.refresh()).resolves.toBeUndefined();

        expect(search.loadError.value).toBe("重试仍失败");
        expect(unhandled).not.toHaveBeenCalled();
        window.removeEventListener("unhandledrejection", unhandled);
    });

    it("旧请求先完成时不提前结束最新请求的加载状态", async () => {
        const oldRequest = deferred<ArchiveRecordListDto>();
        const latestRequest = deferred<ArchiveRecordListDto>();
        mocks.searchArchiveRecords
            .mockImplementationOnce(() => oldRequest.promise)
            .mockImplementationOnce(() => latestRequest.promise);
        const search = renderSearch();

        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        search.submit({ categoryId: 8, conditions: [], relatedGroups: [] });
        oldRequest.resolve({ fields: [], items: [{ id: 1 }] });
        await oldRequest.promise;

        await waitFor(() => expect(search.loading.value).toBe(true));
        latestRequest.resolve({ fields: [], items: [{ id: 2 }] });
        await waitFor(() => expect(search.loading.value).toBe(false));
    });

    it("旧请求后成功时不覆盖最新结果", async () => {
        const oldRequest = deferred<ArchiveRecordListDto>();
        const latestRequest = deferred<ArchiveRecordListDto>();
        mocks.searchArchiveRecords
            .mockImplementationOnce(() => oldRequest.promise)
            .mockImplementationOnce(() => latestRequest.promise);
        const search = renderSearch();

        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        search.submit({ categoryId: 8, conditions: [], relatedGroups: [] });
        latestRequest.resolve({ fields: [], items: [{ id: 2 }], next: "latest-next" });
        await waitFor(() => expect(search.result.value?.items[0]?.id).toBe(2));
        oldRequest.resolve({ fields: [], items: [{ id: 1 }], next: "old-next" });
        await oldRequest.promise;

        await waitFor(() => expect(search.result.value?.items[0]?.id).toBe(2));
        expect(search.result.value?.next).toBe("latest-next");
    });

    it("旧请求后失败时不覆盖最新结果和错误状态", async () => {
        const oldRequest = deferred<ArchiveRecordListDto>();
        const latestRequest = deferred<ArchiveRecordListDto>();
        mocks.searchArchiveRecords
            .mockImplementationOnce(() => oldRequest.promise)
            .mockImplementationOnce(() => latestRequest.promise);
        const search = renderSearch();

        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        search.submit({ categoryId: 8, conditions: [], relatedGroups: [] });
        latestRequest.resolve({ fields: [], items: [{ id: 2 }] });
        await waitFor(() => expect(search.result.value?.items[0]?.id).toBe(2));
        oldRequest.reject(new Error("旧请求失败"));
        await oldRequest.promise.catch(() => undefined);

        await waitFor(() => expect(search.loadError.value).toBeUndefined());
        expect(search.result.value?.items[0]?.id).toBe(2);
    });

    it("重置使在途请求失效并立即结束加载状态", async () => {
        const request = deferred<ArchiveRecordListDto>();
        mocks.searchArchiveRecords.mockImplementationOnce(() => request.promise);
        const search = renderSearch();

        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        expect(search.loading.value).toBe(true);
        search.reset();
        expect(search.loading.value).toBe(false);
        request.resolve({ fields: [], items: [{ id: 1 }] });
        await request.promise;

        await waitFor(() => expect(search.result.value).toBeUndefined());
    });

    it("刷新 Promise 在实际查询完成后才结束", async () => {
        const search = renderSearch();
        search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(1));
        const request = deferred<ArchiveRecordListDto>();
        mocks.searchArchiveRecords.mockImplementationOnce(() => request.promise);

        const refreshPromise = search.refresh();

        expect(refreshPromise).toBeInstanceOf(Promise);
        let settled = false;
        void refreshPromise.then(() => (settled = true));
        await Promise.resolve();
        expect(settled).toBe(false);

        request.resolve({ fields: [], items: [{ id: 2 }] });
        await refreshPromise;
        expect(settled).toBe(true);
        expect(search.result.value?.items[0]?.id).toBe(2);
    });

    it("没有已提交查询时刷新返回已完成 Promise", async () => {
        const search = renderSearch();

        await expect(search.refresh()).resolves.toBeUndefined();

        expect(mocks.searchArchiveRecords).not.toHaveBeenCalled();
    });

    it("修改分类草稿保留已提交查询和结果且翻页仍使用旧查询", async () => {
        const search = renderSearch();
        search.queryForm.categoryId = 7;
        await waitFor(() => expect(mocks.listArchiveFields).toHaveBeenCalledWith(7, "ITEM"));
        search.submit({
            categoryId: 7,
            fondsCode: "F-OLD",
            conditions: [],
            relatedGroups: [],
        });
        await waitFor(() => expect(search.result.value?.next).toBe("next-2"));

        search.queryForm.fondsCode = "F-DRAFT";
        search.queryForm.categoryId = 8;
        await waitFor(() => expect(mocks.listArchiveFields).toHaveBeenCalledWith(8, "ITEM"));

        expect(search.committedQuery.value?.categoryId).toBe(7);
        expect(search.result.value?.next).toBe("next-2");
        search.page("next-2");
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(2));
        expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({
                categoryId: 7,
                fondsCode: "F-OLD",
                cursor: "next-2",
            }),
        );
    });
});

function renderSearch() {
    let search!: ReturnType<typeof useArchiveItemSearch>;
    render(
        defineComponent({
            setup() {
                search = useArchiveItemSearch();
                return () => h("div");
            },
        }),
    );
    return search;
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
