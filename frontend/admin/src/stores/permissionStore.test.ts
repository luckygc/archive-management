import { createPinia, setActivePinia } from "pinia";
import { watch } from "vue";
import { afterEach, beforeEach, describe, expect, expectTypeOf, it, vi } from "vite-plus/test";

import { type PermissionSnapshot, usePermissionStore } from "./permissionStore";

const PERMISSION_REFRESH_INTERVAL_MS = 60_000;
const PERMISSION_SNAPSHOT_TTL_MS = 300_000;

const mocks = vi.hoisted(() => ({ getCurrentUserPermissions: vi.fn() }));
vi.mock("@/shared/api/authorization", () => mocks);

describe("permissionStore", () => {
    beforeEach(() => {
        setActivePinia(createPinia());
        vi.resetAllMocks();
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-07-15T00:00:00Z"));
    });

    afterEach(() => vi.useRealTimers());

    it("超级管理员拥有未枚举权限", async () => {
        const store = usePermissionStore();
        mocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: true,
            permissionCodes: [],
        });

        await store.fetchSummary();

        expect(store.has("future:permission")).toBe(true);
    });

    it("普通用户只拥有服务端返回的权限码", async () => {
        const store = usePermissionStore();
        await grant(store, ["archive:item:read"]);

        expect(store.has("archive:item:read")).toBe(true);
        expect(store.has("archive:item:update")).toBe(false);
    });

    it("对外权限快照和权限码在类型与运行时均深只读", async () => {
        const store = usePermissionStore();
        await grant(store, ["archive:item:read"]);

        expectTypeOf(store.snapshot).toEqualTypeOf<Readonly<PermissionSnapshot>>();
        expectTypeOf(store.snapshot.permissionCodes).toEqualTypeOf<readonly string[]>();
        expect(Object.isFrozen(store.snapshot)).toBe(true);
        expect(Object.isFrozen(store.snapshot.permissionCodes)).toBe(true);
        expect(() =>
            (store.snapshot.permissionCodes as string[]).push("archive:item:update"),
        ).toThrow(TypeError);
        const currentSnapshot = store.snapshot;
        const warning = vi.spyOn(console, "warn").mockImplementation(() => undefined);
        try {
            Reflect.set(store, "snapshot", { ...currentSnapshot, revision: 999 });
        } catch {
            // Vue 可能拒绝只读 computed 赋值并抛错，也可能只发出只读警告。
        }
        warning.mockRestore();
        expect(store.snapshot).toBe(currentSnapshot);
        expect(store.snapshot.revision).not.toBe(999);
    });

    it("有效期内刷新失败时保留完整旧快照", async () => {
        const store = usePermissionStore();
        await grant(store, ["archive:item:read"], true);
        const previous = store.snapshot;
        mocks.getCurrentUserPermissions.mockRejectedValue(new Error("permission unavailable"));

        await expect(store.fetchSummary()).rejects.toThrow("permission unavailable");

        expect(store.snapshot).toBe(previous);
        expect(store.ready).toBe(true);
        expect(store.has("archive:item:read")).toBe(true);
    });

    it("提前刷新失败后只保留到真实 TTL，到期立即 fail-closed", async () => {
        const store = usePermissionStore();
        await grant(store, ["archive:item:read"]);
        mocks.getCurrentUserPermissions.mockRejectedValue(new Error("permission unavailable"));
        vi.advanceTimersByTime(PERMISSION_SNAPSHOT_TTL_MS - PERMISSION_REFRESH_INTERVAL_MS);

        await expect(store.refreshIfNeeded()).rejects.toThrow("permission unavailable");
        expect(store.ready).toBe(true);
        expect(store.has("archive:item:read")).toBe(true);

        vi.advanceTimersByTime(PERMISSION_REFRESH_INTERVAL_MS);
        const refresh = store.refreshIfNeeded();

        expect(store.expired).toBe(true);
        expect(store.ready).toBe(false);
        expect(store.has("archive:item:read")).toBe(false);
        await expect(refresh).rejects.toThrow("permission unavailable");
    });

    it("提前刷新成功直接延长 TTL，不经过 expired 状态", async () => {
        const store = usePermissionStore();
        await grant(store, ["archive:item:read"]);
        vi.advanceTimersByTime(PERMISSION_SNAPSHOT_TTL_MS - PERMISSION_REFRESH_INTERVAL_MS);
        mocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: false,
            permissionCodes: ["archive:item:read"],
        });
        const observed: boolean[] = [];
        watch(
            () => store.snapshot,
            () => observed.push(store.expired),
            { flush: "sync" },
        );

        await store.refreshIfNeeded();

        expect(observed).toEqual([false]);
        expect(store.ready).toBe(true);
        expect(store.snapshot.validUntil).toBe(Date.now() + PERMISSION_SNAPSHOT_TTL_MS);
    });

    it("过期失败后恢复成功可重新验证权限", async () => {
        const store = usePermissionStore();
        await grant(store, ["archive:item:read"]);
        vi.advanceTimersByTime(PERMISSION_SNAPSHOT_TTL_MS);
        mocks.getCurrentUserPermissions.mockRejectedValueOnce(new Error("permission unavailable"));
        await expect(store.ensureFresh()).rejects.toThrow("permission unavailable");
        expect(store.ready).toBe(false);

        mocks.getCurrentUserPermissions.mockResolvedValueOnce({
            superAdmin: false,
            permissionCodes: ["archive:item:read"],
        });
        await store.ensureFresh();

        expect(store.ready).toBe(true);
        expect(store.has("archive:item:read")).toBe(true);
    });

    it("普通用户刷新为超级管理员时只提交一个完整权限状态", async () => {
        const store = usePermissionStore();
        await grant(store, ["archive:item:read"]);
        mocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: true,
            permissionCodes: [],
        });
        const observed: Array<{ superAdmin: boolean; permissionCodes: readonly string[] }> = [];
        watch(
            () => store.snapshot,
            () =>
                observed.push({
                    superAdmin: store.superAdmin,
                    permissionCodes: store.permissionCodes,
                }),
            { flush: "sync" },
        );

        await store.fetchSummary();

        expect(observed).toEqual([{ superAdmin: true, permissionCodes: [] }]);
    });

    it("并发刷新复用同一个在途请求且只提交一次", async () => {
        const store = usePermissionStore();
        const request = deferred<{ superAdmin: boolean; permissionCodes: string[] }>();
        mocks.getCurrentUserPermissions.mockReturnValue(request.promise);
        const revisions: number[] = [];
        watch(
            () => store.snapshot,
            () => revisions.push(store.snapshot.revision),
            {
                flush: "sync",
            },
        );

        const firstRefresh = store.fetchSummary();
        const secondRefresh = store.fetchSummary();

        expect(mocks.getCurrentUserPermissions).toHaveBeenCalledTimes(1);
        request.resolve({ superAdmin: true, permissionCodes: [] });
        await Promise.all([firstRefresh, secondRefresh]);
        expect(revisions).toEqual([1]);
    });

    it("reset 使旧请求失效并允许立即发起新请求", async () => {
        const store = usePermissionStore();
        const oldRequest = deferred<{ superAdmin: boolean; permissionCodes: string[] }>();
        mocks.getCurrentUserPermissions
            .mockReturnValueOnce(oldRequest.promise)
            .mockResolvedValueOnce({
                superAdmin: false,
                permissionCodes: ["archive:item:update"],
            });

        const oldRefresh = store.fetchSummary();
        store.reset();
        await store.fetchSummary();
        oldRequest.resolve({ superAdmin: true, permissionCodes: ["archive:item:read"] });
        await oldRefresh;

        expect(mocks.getCurrentUserPermissions).toHaveBeenCalledTimes(2);
        expect(store.ready).toBe(true);
        expect(store.superAdmin).toBe(false);
        expect(store.permissionCodes).toEqual(["archive:item:update"]);
    });
});

async function grant(
    store: ReturnType<typeof usePermissionStore>,
    permissionCodes: string[],
    superAdmin = false,
) {
    mocks.getCurrentUserPermissions.mockResolvedValueOnce({ superAdmin, permissionCodes });
    await store.fetchSummary();
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
