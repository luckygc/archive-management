import { describe, expect, it, vi } from "vite-plus/test";

import {
    searchArchiveRecords,
    updateArchiveFonds,
    uploadArchiveItemElectronicFile,
} from "./archive";

const httpClientMock = vi.hoisted(() => ({
    delete: vi.fn(),
    download: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
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
            "/api/v1/archive-items:search?limit=100&cursor=next-token&requestTotal=true",
            {
                categoryId: 1,
                keyword: "合同",
                orderBy: [{ field: "createdAt", direction: "DESC" }],
            },
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
});
