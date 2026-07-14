import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { usePermissionStore } from "./permissionStore";

const mocks = vi.hoisted(() => ({ getCurrentUserPermissions: vi.fn() }));
vi.mock("@/shared/api/authorization", () => mocks);

describe("permissionStore", () => {
    beforeEach(() => {
        setActivePinia(createPinia());
        vi.resetAllMocks();
    });

    it("超级管理员拥有未枚举权限", () => {
        const store = usePermissionStore();
        store.$patch({ superAdmin: true, permissionCodes: [] });

        expect(store.has("future:permission")).toBe(true);
    });

    it("普通用户只拥有服务端返回的权限码", () => {
        const store = usePermissionStore();
        store.$patch({
            superAdmin: false,
            permissionCodes: ["archive:item:read"],
        });

        expect(store.has("archive:item:read")).toBe(true);
        expect(store.has("archive:item:update")).toBe(false);
    });

    it("权限摘要加载失败时保持未初始化并清空旧权限", async () => {
        const store = usePermissionStore();
        store.$patch({
            initialized: true,
            superAdmin: true,
            permissionCodes: ["archive:item:read"],
        });
        mocks.getCurrentUserPermissions.mockRejectedValue(new Error("permission unavailable"));

        await expect(store.fetchSummary()).rejects.toThrow("permission unavailable");

        expect(store.initialized).toBe(false);
        expect(store.superAdmin).toBe(false);
        expect(store.permissionCodes).toEqual([]);
    });
});
