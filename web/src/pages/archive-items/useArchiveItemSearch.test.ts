import { cleanup, render, waitFor } from "@testing-library/vue";
import { defineComponent, h } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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
        search.refresh();
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(3));
        expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith(
            expect.objectContaining({ categoryId: 7, limit: 100, cursor: "next-2" }),
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
