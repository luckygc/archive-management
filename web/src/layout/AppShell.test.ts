import { cleanup, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { nextTick } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, describe, expect, it, vi } from "vite-plus/test";

import { usePageTabsStore } from "@/stores/pageTabsStore";
import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";
import AppShell from "./AppShell.vue";

afterEach(cleanup);

describe("AppShell", () => {
    it("当前页权限被收回时立即卸载缓存内容、清理页签并进入 403", async () => {
        const { permissionStore, router, tabsStore } = await renderShell({
            initialPath: "/archive/items",
            permissionCodes: ["archive:item:read", "authentication:user:manage"],
        });
        expect(await screen.findByText("敏感档案内容")).toBeInTheDocument();

        permissionStore.permissionCodes = [];

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

        permissionStore.permissionCodes = ["archive:item:read"];

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
});

async function renderShell({
    initialPath,
    permissionCodes,
}: {
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
    permissionStore.initialized = true;
    permissionStore.permissionCodes = permissionCodes;
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
    await router.push(initialPath);
    await router.isReady();

    render({ template: "<RouterView />" }, { global: { plugins: [ElementPlus, pinia, router] } });
    return { permissionStore, router, tabsStore };
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
