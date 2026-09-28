import { beforeEach, describe, expect, it, vi } from "vite-plus/test";

import {
    closeArchiveFonds,
    updateArchiveFonds,
    updateArchiveRetentionPeriod,
    updateArchiveSecurityLevel,
} from "./archive-metadata";
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

beforeEach(() => vi.clearAllMocks());

describe("archive API", () => {
    it("updates archive fonds through the resource PATCH endpoint", async () => {
        const payload = {
            fondsName: "华东公司",
            sortOrder: 10,
        };
        httpClientMock.patch.mockResolvedValue({ id: 1, ...payload });

        await updateArchiveFonds(1, payload);

        expect(httpClientMock.patch).toHaveBeenCalledWith("/archive-fonds/1", payload, {
            headers: { "Content-Type": "application/merge-patch+json" },
        });
    });

    it("通过自定义动作封闭全宗", async () => {
        const payload = { reason: "机构撤并", effectiveAt: "2026-08-01T10:00:00" };
        httpClientMock.post.mockResolvedValue({ id: 1, status: "CLOSED" });

        await closeArchiveFonds(1, payload);

        expect(httpClientMock.post).toHaveBeenCalledWith("/archive-fonds/1:close", payload);
    });

    it("密级和保管期限名称更新使用 Merge Patch", async () => {
        await updateArchiveSecurityLevel(3, { levelName: "秘密" });
        await updateArchiveRetentionPeriod(4, { periodName: "永久" });

        expect(httpClientMock.patch).toHaveBeenCalledWith(
            "/archive-security-levels/3",
            { levelName: "秘密" },
            { headers: { "Content-Type": "application/merge-patch+json" } },
        );
        expect(httpClientMock.patch).toHaveBeenCalledWith(
            "/archive-retention-periods/4",
            { periodName: "永久" },
            { headers: { "Content-Type": "application/merge-patch+json" } },
        );
    });

    it("档案搜索沿用分页链接并保留搜索请求体", async () => {
        httpClientMock.post.mockResolvedValue({ fields: [], items: [] });

        await searchArchiveRecords({
            categoryId: 1,
            keyword: "合同",
            limit: 100,
            cursor: "/archive-items:search?limit=100&cursor=next-token",
            orderBy: [{ field: "createdAt", direction: "DESC" }],
        });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/archive-items:search?limit=100&cursor=next-token",
            {
                categoryId: 1,
                keyword: "合同",
                orderBy: [{ field: "createdAt", direction: "DESC" }],
            },
        );
    });

    it("档案首页搜索默认不请求总数", async () => {
        await searchArchiveRecords({ categoryId: 1, limit: 100 });

        expect(httpClientMock.post).toHaveBeenCalledWith("/archive-items:search?limit=100", {
            categoryId: 1,
        });
    });

    it("为导入模板创建短链后返回浏览器可直接打开的地址", async () => {
        httpClientMock.post.mockResolvedValue({
            url: "/file-links/template-code:download",
            expiresAt: "2026-07-15T10:10:00",
        });
        httpClientMock.download.mockReturnValue({
            href: "/file-links/template-code:download",
        });

        await downloadArchiveImportTemplate(11);

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/archive-categories/11/archive-items:createImportTemplateDownloadLink",
        );
        expect(httpClientMock.download).toHaveBeenCalledWith("/file-links/template-code:download");
    });

    it("导出只提交业务查询并通过短链下载", async () => {
        httpClientMock.post.mockResolvedValue({
            url: "/file-links/export-code:download",
            expiresAt: "2026-07-15T10:10:00",
        });
        httpClientMock.download.mockReturnValue({
            href: "/file-links/export-code:download",
        });

        await exportArchiveRecords({
            categoryId: 1,
            volumeId: 77,
            keyword: "合同",
            limit: 100,
            cursor: "ignored",
        });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/archive-items:createExportDownloadLink",
            { categoryId: 1, volumeId: 77, keyword: "合同" },
        );
        expect(httpClientMock.download).toHaveBeenCalledWith("/file-links/export-code:download");
    });

    it("uploads archive item electronic file as multipart under archive item", async () => {
        const file = new File(["demo"], "合同.pdf", { type: "application/pdf" });
        httpClientMock.post.mockResolvedValue({ id: 10 });

        await uploadArchiveItemElectronicFile(1, file, { usageType: "DEFAULT", displayOrder: 2 });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/archive-items/1/electronic-files",
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

        expect(httpClientMock.request).toHaveBeenCalledWith("/archive-items/9", {
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
            cursor: "/archive-items/1/relations?depth=2&limit=100&cursor=next-relation",
        });

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/archive-items/1/relations?depth=2&limit=100&cursor=next-relation",
        );
    });

    it("创建和删除关系复用档案关系子资源", async () => {
        httpClientMock.post.mockResolvedValue({ id: 8 });
        httpClientMock.delete.mockResolvedValue(undefined);

        await createArchiveItemRelation(1, 2);
        await deleteArchiveItemRelation(1, 8);

        expect(httpClientMock.post).toHaveBeenCalledWith("/archive-items/1/relations", {
            targetItemId: 2,
        });
        expect(httpClientMock.delete).toHaveBeenCalledWith("/archive-items/1/relations/8");
    });
});
