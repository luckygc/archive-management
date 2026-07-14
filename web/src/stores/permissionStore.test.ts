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
        setSnapshot(store, { superAdmin: true, permissionCodes: [] });

        expect(store.has("future:permission")).toBe(true);
    });

    it("普通用户只拥有服务端返回的权限码", () => {
        const store = usePermissionStore();
        setSnapshot(store, {
            superAdmin: false,
            permissionCodes: ["archive:item:read"],
        });

        expect(store.has("archive:item:read")).toBe(true);
        expect(store.has("archive:item:update")).toBe(false);
    });

    it("已初始化的权限摘要刷新失败时保留完整旧快照", async () => {
        const store = usePermissionStore();
        setSnapshot(store, {
            superAdmin: true,
            permissionCodes: ["archive:item:read"],
        });
        mocks.getCurrentUserPermissions.mockRejectedValue(new Error("permission unavailable"));

        await expect(store.fetchSummary()).rejects.toThrow("permission unavailable");

        expect(store.initialized).toBe(true);
        expect(store.superAdmin).toBe(true);
        expect(store.permissionCodes).toEqual(["archive:item:read"]);
    });

    it("普通用户刷新为超级管理员时只提交一个完整权限状态", async () => {
        const store = usePermissionStore();
        setSnapshot(store, {
            superAdmin: false,
            permissionCodes: ["archive:item:read"],
        });
        mocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: true,
            permissionCodes: [],
        });
        const observed: Array<{ superAdmin: boolean; permissionCodes: string[] }> = [];
        store.$subscribe(
            () =>
                observed.push({
                    superAdmin: store.superAdmin,
                    permissionCodes: [...store.permissionCodes],
                }),
            { flush: "sync" },
        );

        await store.fetchSummary();

        expect(observed).toEqual([{ superAdmin: true, permissionCodes: [] }]);
    });

    it("并发刷新只提交最后发起请求的权限摘要", async () => {
        const store = usePermissionStore();
        const first = deferred<{ superAdmin: boolean; permissionCodes: string[] }>();
        const second = deferred<{ superAdmin: boolean; permissionCodes: string[] }>();
        mocks.getCurrentUserPermissions
            .mockReturnValueOnce(first.promise)
            .mockReturnValueOnce(second.promise);

        const firstRefresh = store.fetchSummary();
        const secondRefresh = store.fetchSummary();
        second.resolve({ superAdmin: true, permissionCodes: [] });
        await secondRefresh;
        first.resolve({ superAdmin: false, permissionCodes: ["archive:item:read"] });
        await firstRefresh;

        expect(store.initialized).toBe(true);
        expect(store.superAdmin).toBe(true);
        expect(store.permissionCodes).toEqual([]);
    });

    it("reset 使仍在途的权限摘要响应失效", async () => {
        const store = usePermissionStore();
        const request = deferred<{ superAdmin: boolean; permissionCodes: string[] }>();
        mocks.getCurrentUserPermissions.mockReturnValue(request.promise);

        const refresh = store.fetchSummary();
        store.reset();
        request.resolve({ superAdmin: true, permissionCodes: ["archive:item:read"] });
        await refresh;

        expect(store.initialized).toBe(false);
        expect(store.superAdmin).toBe(false);
        expect(store.permissionCodes).toEqual([]);
    });
});

function setSnapshot(
    store: ReturnType<typeof usePermissionStore>,
    state: { superAdmin: boolean; permissionCodes: string[] },
) {
    store.snapshot = {
        initialized: true,
        permissionCodes: state.permissionCodes,
        revision: store.snapshot.revision + 1,
        superAdmin: state.superAdmin,
    };
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, resolve, reject };
}
