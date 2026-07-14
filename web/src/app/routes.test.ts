import { createPinia, setActivePinia } from "pinia";
import type { RouteRecordRaw } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";

import {
    canAccessRoute,
    navigationGuard,
    normalizeRedirect,
    router,
    workspaceRoutes,
} from "./routes";

const permissionApiMocks = vi.hoisted(() => ({ getCurrentUserPermissions: vi.fn() }));
vi.mock("@/shared/api/authorization", () => permissionApiMocks);

beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
});
afterEach(() => vi.useRealTimers());

const PERMISSION_SNAPSHOT_TTL_MS = 300_000;

describe("workspaceRoutes", () => {
    it("由嵌套路由同时提供菜单与面包屑层级", () => {
        const resolved = router.resolve("/archive/catalog/fonds");

        expect(resolved.matched.map((item) => item.meta.title).filter(Boolean)).toEqual([
            "目录配置",
            "全宗管理",
        ]);
        expect(workspaceRoutes.find((item) => item.path === "archive/catalog")?.meta?.menu).toBe(
            true,
        );
    });

    it("业务页默认缓存并允许显式关闭", () => {
        expect(router.resolve("/archive/items").meta.cache).toBe(true);
        expect(router.resolve("/intake").meta.cache).toBe(false);
    });

    it("案卷管理路由懒加载页面并复用档案读取权限", () => {
        const resolved = router.resolve("/archive/volumes");
        const route = workspaceRoutes.find((item) => item.path === "archive/volumes");

        expect(resolved.name).toBe("archive-volumes");
        expect(resolved.meta.title).toBe("案卷管理");
        expect(resolved.meta.permission).toBe("archive:item:read");
        expect(typeof route?.component).toBe("function");
    });

    it("为已交付业务页和系统页标注服务端权限并隐藏占位入口", () => {
        expect(router.resolve("/archive/library").meta.permission).toBe("archive:item:read");
        expect(router.resolve("/archive/items").meta.permission).toBe("archive:item:read");
        expect(router.resolve("/archive/catalog/categories").meta.permission).toBe(
            "archive:metadata:manage",
        );
        expect(router.resolve("/archive/governance/schemes").meta.permission).toBe(
            "archive:governance:manage",
        );
        expect(router.resolve("/system/users").meta.permission).toBe("authentication:user:manage");
        expect(router.resolve("/system/authorization").meta.permissionsAnyOf).toEqual([
            "authorization:permission:manage",
            "archive:data-scope:manage",
        ]);
        expect(router.resolve("/system/roles").meta.permission).toBe("authorization:role:manage");
        expect(router.resolve("/system/data-scopes").meta.permission).toBe(
            "archive:data-scope:manage",
        );
        expect(router.resolve("/system/organization-departments").meta.permission).toBe(
            "organization:department:manage",
        );
        expect(router.resolve("/system/login-sessions").meta.permission).toBe(
            "authentication:session:manage",
        );
        expect(router.resolve("/system/authentication-events").meta.permission).toBe(
            "authentication:audit:read",
        );
        expect(router.resolve("/intake").meta.menu).toBe(false);
        expect(router.resolve("/system/storage").meta.menu).toBe(false);
        expect(router.resolve("/system/settings").meta.menu).toBe(false);
    });

    it("隐藏无权限叶子和没有可见子项的菜单分组", () => {
        const checker = { has: (code: string) => code === "archive:item:read" };
        const allowedLeaf: RouteRecordRaw = {
            path: "items",
            component: {},
            meta: { menu: true, permission: "archive:item:read" },
        };
        const deniedGroup: RouteRecordRaw = {
            path: "system",
            meta: { menu: true },
            children: [
                {
                    path: "users",
                    component: {},
                    meta: { menu: true, permission: "authentication:user:manage" },
                },
            ],
        };

        expect(canAccessRoute(allowedLeaf, checker)).toBe(true);
        expect(canAccessRoute(deniedGroup, checker)).toBe(false);
    });

    it("menu:false 功能和非菜单父路由不因没有菜单子项被拒绝", () => {
        const checker = { has: () => true };

        expect(
            canAccessRoute({ path: "hidden", component: {}, meta: { menu: false } }, checker),
        ).toBe(true);
        expect(
            canAccessRoute(
                {
                    path: "parent",
                    children: [{ path: "hidden", component: {}, meta: { menu: false } }],
                },
                checker,
            ),
        ).toBe(true);
    });

    it("菜单分组只有隐藏子项时不显示空分组", () => {
        const checker = { has: () => true };
        const emptyMenuGroup: RouteRecordRaw = {
            path: "unfinished",
            meta: { menu: true },
            children: [{ path: "hidden", component: {}, meta: { menu: false } }],
        };

        expect(canAccessRoute(emptyMenuGroup, checker)).toBe(false);
    });

    it("显式空 children 的菜单分组不会退化为可点击叶子", () => {
        const checker = { has: () => true };

        expect(canAccessRoute({ path: "empty", meta: { menu: true }, children: [] }, checker)).toBe(
            false,
        );
    });

    it("any-of 路由权限满足任意一项即可访问", () => {
        const route: RouteRecordRaw = {
            path: "authorization",
            component: {},
            meta: {
                menu: true,
                permissionsAnyOf: ["authorization:permission:manage", "archive:data-scope:manage"],
            },
        };

        expect(
            canAccessRoute(route, {
                has: (code) => code === "archive:data-scope:manage",
            }),
        ).toBe(true);
        expect(canAccessRoute(route, { has: () => false })).toBe(false);
    });

    it("权限初始化完成后将无权直达请求定向到 403", async () => {
        await authenticate();

        await expect(navigationGuard(router.resolve("/archive/items"))).resolves.toEqual({
            name: "forbidden",
            replace: true,
        });
    });

    it("权限尚未初始化时等待加载而不提前判定无权", async () => {
        await authenticate({ initializedPermissions: false });
        permissionApiMocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: false,
            permissionCodes: ["archive:item:read"],
        });

        await expect(navigationGuard(router.resolve("/archive/items"))).resolves.toBe(true);
    });

    it("权限摘要加载失败时进入会话校验失败页而不是误判 403", async () => {
        await authenticate({ initializedPermissions: false });
        permissionApiMocks.getCurrentUserPermissions.mockRejectedValue(
            new Error("permission unavailable"),
        );

        await expect(navigationGuard(router.resolve("/archive/items"))).resolves.toEqual({
            path: "/authentication-error",
            query: { redirect: "/archive/items" },
            replace: true,
        });
    });

    it("403 页面本身不会再次重定向", async () => {
        await authenticate();

        await expect(navigationGuard(router.resolve("/forbidden"))).resolves.toBe(true);
    });

    it("仅数据范围权限可直接进入授权管理", async () => {
        await authenticate({ permissionCodes: ["archive:data-scope:manage"] });

        await expect(navigationGuard(router.resolve("/system/authorization"))).resolves.toBe(true);
    });

    it("权限快照过期且重新校验失败时进入校验失败页而不是沿用旧授权", async () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-07-15T00:00:00Z"));
        await authenticate({ permissionCodes: ["archive:item:read"] });
        vi.advanceTimersByTime(PERMISSION_SNAPSHOT_TTL_MS);
        permissionApiMocks.getCurrentUserPermissions.mockRejectedValue(
            new Error("permission unavailable"),
        );

        await expect(navigationGuard(router.resolve("/archive/items"))).resolves.toEqual({
            path: "/authentication-error",
            query: { redirect: "/archive/items" },
            replace: true,
        });
        expect(permissionApiMocks.getCurrentUserPermissions).toHaveBeenCalledTimes(2);
    });

    it("在路由边界清洗登录后跳转地址", () => {
        expect(normalizeRedirect("/archive/items?fonds=A")).toBe("/archive/items?fonds=A");
        expect(normalizeRedirect("//outside.example")).toBe("/");
        expect(normalizeRedirect("https://outside.example")).toBe("/");
        expect(normalizeRedirect(["/archive/items"])).toBe("/");
    });
});

async function authenticate({
    initializedPermissions = true,
    permissionCodes = [],
}: {
    initializedPermissions?: boolean;
    permissionCodes?: string[];
} = {}) {
    const sessionStore = useSessionStore();
    sessionStore.initialized = true;
    sessionStore.currentUser = {
        sessionId: "session-1",
        username: "reader",
        displayName: "只读用户",
        roles: [],
    };
    const permissionStore = usePermissionStore();
    if (initializedPermissions) {
        permissionApiMocks.getCurrentUserPermissions.mockResolvedValueOnce({
            superAdmin: false,
            permissionCodes,
        });
        await permissionStore.fetchSummary();
    }
    return { permissionStore, sessionStore };
}
