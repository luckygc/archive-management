import { cleanup, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { nextTick } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { usePageTabsStore } from "@/stores/pageTabsStore";
import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";
import AppShell from "./AppShell.vue";

const permissionApiMocks = vi.hoisted(() => ({ getCurrentUserPermissions: vi.fn() }));
vi.mock("@/shared/api/authorization", () => permissionApiMocks);

beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

describe("AppShell", () => {
    it("当前页权限被收回时立即卸载缓存内容、清理页签并进入 403", async () => {
        const { permissionStore, router, tabsStore } = await renderShell({
            initialPath: "/archive/items",
            permissionCodes: ["archive:item:read", "authentication:user:manage"],
        });
        expect(await screen.findByText("敏感档案内容")).toBeInTheDocument();

        permissionApiMocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: false,
            permissionCodes: [],
        });
        await permissionStore.fetchSummary();

        await waitFor(() => expect(router.currentRoute.value.name).toBe("forbidden"));
        expect(screen.queryByText("敏感档案内容")).not.toBeInTheDocument();
        expect(tabsStore.tabs.map((item) => item.fullPath)).toEqual(["/"]);
        expect(tabsStore.activeFullPath).toBe("/");
        expect(tabsStore.tabs.some((item) => item.fullPath === tabsStore.activeFullPath)).toBe(
            true,
        );
    });

    it("权限收回时清理非当前失权页签且不影响当前有权页面", async () => {
        const { permissionStore, router, tabsStore } = await renderShell({
            initialPath: "/archive/items",
            permissionCodes: ["archive:item:read", "authentication:user:manage"],
        });

        permissionApiMocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: false,
            permissionCodes: ["archive:item:read"],
        });
        await permissionStore.fetchSummary();

        await waitFor(() =>
            expect(tabsStore.tabs.map((item) => item.fullPath)).toEqual(["/", "/archive/items"]),
        );
        expect(router.currentRoute.value.fullPath).toBe("/archive/items");
        expect(tabsStore.activeFullPath).toBe("/archive/items");
        expect(screen.getByText("敏感档案内容")).toBeInTheDocument();
    });

    it("退出时重置权限和页签不会抢先产生额外导航", async () => {
        const { permissionStore, router, tabsStore } = await renderShell({
            initialPath: "/archive/items",
            permissionCodes: ["archive:item:read"],
        });
        const replace = vi.spyOn(router, "replace");

        permissionStore.reset();
        tabsStore.reset();
        await nextTick();

        expect(replace).not.toHaveBeenCalled();
    });

    it("已初始化权限刷新失败时保留页面、页签和当前路由", async () => {
        const { permissionStore, router, tabsStore } = await renderShell({
            initialPath: "/archive/items",
            permissionCodes: ["archive:item:read"],
        });
        permissionApiMocks.getCurrentUserPermissions.mockRejectedValue(
            new Error("permission unavailable"),
        );

        await expect(permissionStore.fetchSummary()).rejects.toThrow("permission unavailable");
        await nextTick();

        expect(permissionStore.initialized).toBe(true);
        expect(router.currentRoute.value.fullPath).toBe("/archive/items");
        expect(tabsStore.tabs.map((item) => item.fullPath)).toContain("/archive/items");
        expect(screen.getByText("敏感档案内容")).toBeInTheDocument();
    });

    it("普通用户刷新为超级管理员时不会经过空权限状态误撤页签", async () => {
        const { permissionStore, router, tabsStore } = await renderShell({
            initialPath: "/archive/items",
            permissionCodes: ["archive:item:read", "authentication:user:manage"],
        });
        permissionApiMocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: true,
            permissionCodes: [],
        });

        await permissionStore.fetchSummary();
        await nextTick();

        expect(router.currentRoute.value.fullPath).toBe("/archive/items");
        expect(tabsStore.tabs.map((item) => item.fullPath)).toEqual([
            "/",
            "/archive/items",
            "/system/users",
        ]);
        expect(screen.getByText("敏感档案内容")).toBeInTheDocument();
    });

    it("权限快速撤销后恢复时过期 403 导航不删除或丢失当前页签", async () => {
        const { permissionStore, router, tabsStore } = await renderShell({
            initialPath: "/archive/items",
            permissionCodes: ["archive:item:read"],
        });
        const navigation = deferred<undefined>();
        const replace = vi.spyOn(router, "replace").mockReturnValueOnce(navigation.promise);
        permissionApiMocks.getCurrentUserPermissions
            .mockResolvedValueOnce({ superAdmin: false, permissionCodes: [] })
            .mockResolvedValueOnce({
                superAdmin: false,
                permissionCodes: ["archive:item:read"],
            });

        await permissionStore.fetchSummary();
        await waitFor(() => expect(replace).toHaveBeenCalledWith({ name: "forbidden" }));
        await permissionStore.fetchSummary();
        navigation.resolve(undefined);
        await navigation.promise;
        await nextTick();

        expect(router.currentRoute.value.fullPath).toBe("/archive/items");
        expect(tabsStore.tabs.map((item) => item.fullPath)).toContain("/archive/items");
        expect(screen.getByText("敏感档案内容")).toBeInTheDocument();
    });

    it("403 导航被守卫取消时保留当前页签并显示内联无权限状态", async () => {
        const { permissionStore, router, tabsStore } = await renderShell({
            blockForbidden: true,
            initialPath: "/archive/items",
            permissionCodes: ["archive:item:read"],
        });
        permissionApiMocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: false,
            permissionCodes: [],
        });

        await permissionStore.fetchSummary();

        expect(await screen.findByText("没有访问权限")).toBeInTheDocument();
        expect(router.currentRoute.value.fullPath).toBe("/archive/items");
        expect(tabsStore.tabs.map((item) => item.fullPath)).toContain("/archive/items");
        expect(screen.queryByText("敏感档案内容")).not.toBeInTheDocument();
        expect(document.querySelectorAll("main")).toHaveLength(1);
    });

    it("403 导航拒绝时不产生未处理异常且保留稳定内联无权限状态", async () => {
        const { permissionStore, router, tabsStore } = await renderShell({
            initialPath: "/archive/items",
            permissionCodes: ["archive:item:read"],
        });
        vi.spyOn(router, "replace").mockRejectedValueOnce(new Error("navigation unavailable"));
        permissionApiMocks.getCurrentUserPermissions.mockResolvedValue({
            superAdmin: false,
            permissionCodes: [],
        });

        await permissionStore.fetchSummary();

        expect(await screen.findByText("没有访问权限")).toBeInTheDocument();
        expect(tabsStore.tabs.map((item) => item.fullPath)).toContain("/archive/items");
        expect(screen.queryByText("敏感档案内容")).not.toBeInTheDocument();
    });
});

async function renderShell({
    blockForbidden = false,
    initialPath,
    permissionCodes,
}: {
    blockForbidden?: boolean;
    initialPath: string;
    permissionCodes: string[];
}) {
    const pinia = createPinia();
    setActivePinia(pinia);
    const sessionStore = useSessionStore();
    sessionStore.initialized = true;
    sessionStore.currentUser = {
        sessionId: "session-1",
        username: "reader",
        displayName: "只读用户",
        roles: [],
    };
    const permissionStore = usePermissionStore();
    permissionApiMocks.getCurrentUserPermissions.mockResolvedValue({
        superAdmin: false,
        permissionCodes,
    });
    await permissionStore.fetchSummary();
    const tabsStore = usePageTabsStore();
    tabsStore.$patch({
        activeFullPath: initialPath,
        tabs: [
            tab("/", "工作台", 0, true),
            tab("/archive/items", "档案管理", 1),
            tab("/system/users", "用户管理", 2),
        ],
    });
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {
                path: "/forbidden",
                name: "forbidden",
                component: { template: "<main>无权页面</main>" },
            },
            {
                path: "/",
                component: AppShell,
                children: [
                    {
                        path: "",
                        component: { template: "<div>工作台内容</div>" },
                        meta: { title: "工作台", affixTab: true },
                    },
                    {
                        path: "archive/items",
                        component: { template: "<div>敏感档案内容</div>" },
                        meta: { title: "档案管理", permission: "archive:item:read" },
                    },
                    {
                        path: "system/users",
                        component: { template: "<div>用户管理内容</div>" },
                        meta: {
                            title: "用户管理",
                            permission: "authentication:user:manage",
                        },
                    },
                ],
            },
        ],
    });
    if (blockForbidden) router.beforeEach((to) => (to.name === "forbidden" ? false : true));
    await router.push(initialPath);
    await router.isReady();

    render(
        { template: "<RouterView />" },
        {
            global: {
                plugins: [ElementPlus, pinia, router],
                stubs: {
                    ElDropdown: { template: "<div><slot /><slot name='dropdown' /></div>" },
                    ElDropdownItem: { template: "<button><slot /></button>" },
                    ElDropdownMenu: { template: "<div><slot /></div>" },
                    ElTooltip: { template: "<span><slot /></span>" },
                },
            },
        },
    );
    return { permissionStore, router, tabsStore };
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((resolvePromise) => {
        resolve = resolvePromise;
    });
    return { promise, resolve };
}

function tab(fullPath: string, title: string, instanceId: number, affix = false) {
    return {
        fullPath,
        title,
        affix,
        cache: true,
        cacheName: `PageTab${instanceId}`,
        instanceId,
        version: 0,
    };
}
