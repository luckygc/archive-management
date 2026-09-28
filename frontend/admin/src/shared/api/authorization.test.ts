import { describe, expect, it, vi } from "vite-plus/test";

import { updateAuthorizationRole } from "./authorization";

const httpClientMock = vi.hoisted(() => ({ patch: vi.fn() }));

vi.mock("@archive-management/frontend-core/api", () => ({ httpClient: httpClientMock }));

describe("authorization API", () => {
    it("角色局部更新以 Merge Patch 发送显式删除的说明", async () => {
        await updateAuthorizationRole(7, { description: null });

        expect(httpClientMock.patch).toHaveBeenCalledWith(
            "/authorization-roles/7",
            { description: null },
            { headers: { "Content-Type": "application/merge-patch+json" } },
        );
    });
});
