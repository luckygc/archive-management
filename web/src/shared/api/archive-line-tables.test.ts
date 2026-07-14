import { describe, expect, it, vi } from "vite-plus/test";

import {
    buildArchiveLineTable,
    createArchiveLineField,
    createArchiveLineTable,
    listArchiveLineFields,
    listArchiveLineTables,
} from "./archive-line-tables";

const httpClientMock = vi.hoisted(() => ({
    get: vi.fn(),
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
});
