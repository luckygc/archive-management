import { describe, expect, it, vi } from "vite-plus/test";

import { getWorkspaceSummary } from "./workspace";

const httpClientMock = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: httpClientMock,
}));

describe("workspace API", () => {
    it("从完整工作台摘要资源读取四项统计", async () => {
        httpClientMock.get.mockResolvedValue({
            archiveItemCount: 12,
            draftCount: 3,
            lockedCount: 2,
            electronicFileCount: 7,
        });

        await expect(getWorkspaceSummary()).resolves.toEqual({
            archiveItemCount: 12,
            draftCount: 3,
            lockedCount: 2,
            electronicFileCount: 7,
        });
        expect(httpClientMock.get).toHaveBeenCalledWith("/api/v1/workspace-summary");
    });
});
