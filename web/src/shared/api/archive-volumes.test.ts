import { describe, expect, it, vi } from "vite-plus/test";

import {
    addArchiveItemToVolume,
    createArchiveVolume,
    getArchiveVolume,
    listArchiveVolumes,
} from "./archive-volumes";

const httpClientMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
}));

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: httpClientMock,
}));

describe("archive volume API", () => {
    it("案卷列表使用 URL query 游标参数", async () => {
        httpClientMock.get.mockResolvedValue({ items: [] });

        await listArchiveVolumes({ fondsCode: "F001", limit: 100, cursor: "next-volume" });

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/api/v1/archive-volumes?fondsCode=F001&limit=100&cursor=next-volume",
        );
    });

    it("创建和详情使用独立案卷资源", async () => {
        const payload = {
            categoryId: 7,
            fondsCode: "F001",
            archiveNo: "V-2026-001",
            archiveYear: 2026,
            electronicStatus: "DRAFT" as const,
        };
        httpClientMock.post.mockResolvedValue({ id: 12 });
        httpClientMock.get.mockResolvedValue({ id: 12 });

        await createArchiveVolume(payload);
        await getArchiveVolume(12);

        expect(httpClientMock.post).toHaveBeenCalledWith("/api/v1/archive-volumes", payload);
        expect(httpClientMock.get).toHaveBeenCalledWith("/api/v1/archive-volumes/12");
    });

    it("将档案加入指定案卷并按 204 处理", async () => {
        httpClientMock.post.mockResolvedValue(undefined);

        const response = addArchiveItemToVolume(12, 91, 3);

        await expect(response).resolves.toBeUndefined();
        expect(httpClientMock.post).toHaveBeenCalledWith("/api/v1/archive-volumes/12:addItem", {
            itemId: 91,
            displayOrder: 3,
        });
    });

    it("未指定卷内顺序时不提交 displayOrder", async () => {
        httpClientMock.post.mockResolvedValue(undefined);

        await addArchiveItemToVolume(12, 91);

        expect(httpClientMock.post).toHaveBeenCalledWith("/api/v1/archive-volumes/12:addItem", {
            itemId: 91,
        });
    });
});
