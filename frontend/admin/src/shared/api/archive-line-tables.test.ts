import { describe, expect, it, vi } from "vite-plus/test";

import {
    buildArchiveLineTable,
    createArchiveItemLineRow,
    createArchiveLineField,
    createArchiveLineTable,
    deleteArchiveItemLineRow,
    listArchiveItemLineTables,
    listArchiveItemLineRows,
    listArchiveLineFields,
    listArchiveLineTables,
    patchArchiveItemLineRow,
} from "./archive-line-tables";

const httpClientMock = vi.hoisted(() => ({
    delete: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
}));

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: httpClientMock,
}));

describe("archive line table API", () => {
    it("使用分类下的明细表集合资源列出和创建定义", async () => {
        const payload = { tableCode: "contract_party", tableName: "合同方", sortOrder: 2 };
        httpClientMock.get.mockResolvedValue({ items: [] });
        httpClientMock.post.mockResolvedValue({ id: 12, ...payload });

        await listArchiveLineTables(7);
        await createArchiveLineTable(7, payload);

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/api/v1/archive-categories/7/item-line-tables",
        );
        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/archive-categories/7/item-line-tables",
            payload,
        );
    });

    it("使用明细表下的字段集合资源列出和创建字段", async () => {
        const payload = {
            fieldCode: "party_name",
            fieldName: "单位名称",
            fieldType: "TEXT" as const,
            columnName: "f_party_name",
            exactSearchable: true,
            sortOrder: 1,
        };
        httpClientMock.get.mockResolvedValue({ items: [] });
        httpClientMock.post.mockResolvedValue({ id: 21, lineTableId: 12, ...payload });

        await listArchiveLineFields(12);
        await createArchiveLineField(12, payload);

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/api/v1/archive-item-line-tables/12/fields",
        );
        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/archive-item-line-tables/12/fields",
            payload,
        );
    });

    it("使用 custom method 显式构建明细表", async () => {
        httpClientMock.post.mockResolvedValue({ id: 12, fields: [] });

        await buildArchiveLineTable(12);

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/archive-item-line-tables/12:build",
        );
    });

    it("使用档案与明细表下的游标集合资源列出和创建行", async () => {
        httpClientMock.get.mockResolvedValue({ items: [] });
        httpClientMock.post.mockResolvedValue({ id: 9 });
        const payload = { lineOrder: 0, values: { amount: "12.50" } };

        await listArchiveItemLineRows(3, 4, { limit: 100, cursor: "next-token" });
        await createArchiveItemLineRow(3, 4, payload);

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/api/v1/archive-items/3/line-tables/4/rows?limit=100&cursor=next-token",
        );
        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/archive-items/3/line-tables/4/rows",
            payload,
        );
    });

    it("普通读取者通过档案范围资源加载已构建明细定义", async () => {
        httpClientMock.get.mockResolvedValue({ items: [] });

        await listArchiveItemLineTables(3);

        expect(httpClientMock.get).toHaveBeenCalledWith("/api/v1/archive-items/3/line-tables");
    });

    it("PATCH 保留显式 null 且删除使用精确行资源", async () => {
        const payload = { values: { remark: null } };

        await patchArchiveItemLineRow(3, 4, 9, payload);
        await deleteArchiveItemLineRow(3, 4, 9);

        expect(httpClientMock.patch).toHaveBeenCalledWith(
            "/api/v1/archive-items/3/line-tables/4/rows/9",
            payload,
        );
        expect(httpClientMock.delete).toHaveBeenCalledWith(
            "/api/v1/archive-items/3/line-tables/4/rows/9",
        );
    });
});
