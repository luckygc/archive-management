import { describe, expect, it, vi } from "vite-plus/test";

import {
    issueServiceAccountToken,
    listAccessTokenCalls,
    revokeAccessToken,
    updateServiceAccount,
} from "./service-account";

const httpClientMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
}));

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: httpClientMock,
}));

describe("服务账号 API", () => {
    it("签发令牌走服务账号子资源，吊销使用冒号动作", async () => {
        httpClientMock.post.mockResolvedValue({ secret: "am1_secret" });
        await issueServiceAccountToken(4, {
            name: "reader",
            expiresAt: "2027-01-01T00:00:00",
        });
        await revokeAccessToken(9);

        expect(httpClientMock.post).toHaveBeenNthCalledWith(1, "/service-accounts/4/access-tokens", {
            name: "reader",
            expiresAt: "2027-01-01T00:00:00",
        });
        expect(httpClientMock.post).toHaveBeenNthCalledWith(2, "/access-tokens/9:revoke");
    });

    it("停用服务账号使用 Merge Patch，调用审计按主体过滤", async () => {
        httpClientMock.get.mockResolvedValue({ items: [] });
        await updateServiceAccount(4, { status: "DISABLED" });
        await listAccessTokenCalls({
            serviceAccountId: 4,
            limit: 100,
            cursor: "/access-token-calls?serviceAccountId=4&limit=100&cursor=next",
        });

        expect(httpClientMock.patch).toHaveBeenCalledWith(
            "/service-accounts/4",
            { status: "DISABLED" },
            { headers: { "Content-Type": "application/merge-patch+json" } },
        );
        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/access-token-calls?serviceAccountId=4&limit=100&cursor=next",
        );
    });
});
