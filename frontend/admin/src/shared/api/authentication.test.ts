import { describe, expect, it, vi } from "vite-plus/test";

import { listAuthenticationUserOptions, updateAuthenticationUser } from "./authentication";

const httpClientMock = vi.hoisted(() => ({
    get: vi.fn(),
    patch: vi.fn(),
}));

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: httpClientMock,
}));

describe("authentication API", () => {
    it("授权用户选项目录使用独立游标资源", async () => {
        httpClientMock.get.mockResolvedValue({ items: [] });
        await listAuthenticationUserOptions(100, "next-user");

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/authentication-user-options?limit=100&cursor=next-user",
        );
    });

    it("用户局部更新以 Merge Patch 发送显式删除的可选字段", async () => {
        await updateAuthenticationUser(7, { email: null });

        expect(httpClientMock.patch).toHaveBeenCalledWith(
            "/authentication-users/7",
            { email: null },
            { headers: { "Content-Type": "application/merge-patch+json" } },
        );
    });
});
