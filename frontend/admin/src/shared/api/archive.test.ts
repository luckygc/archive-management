import { describe, expect, it, vi } from "vite-plus/test";

import { updateArchiveFonds } from "./archive-metadata";
import {
    createArchiveItemRelation,
    deleteArchiveRecord,
    deleteArchiveItemRelation,
    downloadArchiveImportTemplate,
    exportArchiveRecords,
    listArchiveItemRelations,
    searchArchiveRecords,
    uploadArchiveItemElectronicFile,
} from "./archive-records";

const httpClientMock = vi.hoisted(() => ({
    delete: vi.fn(),
    download: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
    request: vi.fn(),
}));

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: httpClientMock,
}));

describe("archive API", () => {
    it("updates archive fonds through the resource PATCH endpoint", async () => {
        const payload = {
            fondsCode: "HD",
            fondsName: "华东公司",
            enabled: false,
            sortOrder: 10,
        };
        httpClientMock.patch.mockResolvedValue({ id: 1, ...payload });

        await updateArchiveFonds(1, payload);

        expect(httpClientMock.patch).toHaveBeenCalledWith("/api/v1/archive-fonds/1", payload);
    });

    it("sends archive record cursor controls in URL and keeps orderBy in body", async () => {
        httpClientMock.post.mockResolvedValue({ fields: [], items: [] });

        await searchArchiveRecords({
            categoryId: 1,
            keyword: "合同",
            limit: 100,
            cursor: "next-token",
            requestTotal: true,
            orderBy: [{ field: "createdAt", direction: "DESC" }],
        });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/archive-items:search?limit=100&cursor=next-token",
            {
                categoryId: 1,
                keyword: "合同",
                orderBy: [{ field: "createdAt", direction: "DESC" }],
            },
        );
    });

    it("为导入模板创建短链后返回浏览器可直接打开的地址", async () => {
        httpClientMock.post.mockResolvedValue({
            url: "/api/v1/file-links/template-code:download",
            expiresAt: "2026-07-15T10:10:00",
        });
        httpClientMock.download.mockReturnValue({
            href: "/api/v1/file-links/template-code:download",
        });

        await downloadArchiveImportTemplate(11);

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/archive-categories/11/archive-items:createImportTemplateDownloadLink",
        );
        expect(httpClientMock.download).toHaveBeenCalledWith(
            "/api/v1/file-links/template-code:download",
        );
    });

    it("导出只提交业务查询并通过短链下载", async () => {
        httpClientMock.post.mockResolvedValue({
            url: "/api/v1/file-links/export-code:download",
            expiresAt: "2026-07-15T10:10:00",
        });
        httpClientMock.download.mockReturnValue({
            href: "/api/v1/file-links/export-code:download",
        });

        await exportArchiveRecords({
            categoryId: 1,
            volumeId: 77,
            keyword: "合同",
            limit: 100,
            cursor: "ignored",
            requestTotal: true,
        });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/archive-items:createExportDownloadLink",
            { categoryId: 1, volumeId: 77, keyword: "合同" },
        );
        expect(httpClientMock.download).toHaveBeenCalledWith(
            "/api/v1/file-links/export-code:download",
        );
    });

    it("uploads archive item electronic file as multipart under archive item", async () => {
        const file = new File(["demo"], "合同.pdf", { type: "application/pdf" });
        httpClientMock.post.mockResolvedValue({ id: 10 });

        await uploadArchiveItemElectronicFile(1, file, { usageType: "DEFAULT", displayOrder: 2 });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/archive-items/1/electronic-files",
            expect.any(FormData),
        );
        const formData = httpClientMock.post.mock.calls.at(-1)?.[1] as FormData;
        expect(formData.get("file")).toBe(file);
        expect(formData.get("usageType")).toBe("DEFAULT");
        expect(formData.get("displayOrder")).toBe("2");
    });

    it("删除档案使用资源 DELETE 和原因请求体", async () => {
        httpClientMock.request.mockResolvedValue(undefined);

        await deleteArchiveRecord(9, "重复数据");

        expect(httpClientMock.request).toHaveBeenCalledWith("/api/v1/archive-items/9", {
            method: "DELETE",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ reason: "重复数据" }),
        });
    });

    it("使用 URL query 游标读取档案关系", async () => {
        httpClientMock.get.mockResolvedValue({ items: [] });

        await listArchiveItemRelations(1, {
            depth: 2,
            limit: 100,
            cursor: "next-relation",
        });

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/api/v1/archive-items/1/relations?depth=2&limit=100&cursor=next-relation",
        );
    });

    it("创建和删除关系复用档案关系子资源", async () => {
        httpClientMock.post.mockResolvedValue({ id: 8 });
        httpClientMock.delete.mockResolvedValue(undefined);

        await createArchiveItemRelation(1, 2);
        await deleteArchiveItemRelation(1, 8);

        expect(httpClientMock.post).toHaveBeenCalledWith("/api/v1/archive-items/1/relations", {
            targetItemId: 2,
        });
        expect(httpClientMock.delete).toHaveBeenCalledWith("/api/v1/archive-items/1/relations/8");
    });
});
