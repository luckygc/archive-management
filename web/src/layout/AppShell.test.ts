import { cleanup, render, screen } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, describe, expect, it } from "vite-plus/test";

import { usePageTabsStore } from "@/stores/pageTabsStore";
import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";
import AppShell from "./AppShell.vue";

afterEach(cleanup);

describe("AppShell", () => {
    it("页签只展示当前用户仍有权访问的功能", async () => {
        const pinia = createPinia();
        setActivePinia(pinia);
        useSessionStore().currentUser = {
            sessionId: "session-1",
            username: "reader",
            displayName: "只读用户",
            roles: [],
        };
        usePermissionStore().permissionCodes = ["archive:item:read"];
        usePageTabsStore().$patch({
            activeFullPath: "/archive/items",
            tabs: [
                tab("/", "工作台", true),
                tab("/archive/items", "档案管理"),
                tab("/system/users", "用户管理"),
            ],
        });
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [
                { path: "/", component: { template: "<div />" } },
                {
                    path: "/archive/items",
                    component: { template: "<div />" },
                    meta: { permission: "archive:item:read" },
                },
                {
                    path: "/system/users",
                    component: { template: "<div />" },
                    meta: { permission: "authentication:user:manage" },
                },
            ],
        });
        await router.push("/");
        await router.isReady();

        render(AppShell, {
            global: {
                plugins: [ElementPlus, pinia, router],
                stubs: { PageTabRouterView: true, RouteMenuItem: true },
            },
        });

        expect(screen.getByRole("tab", { name: "工作台" })).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: "档案管理" })).toBeInTheDocument();
        expect(screen.queryByRole("tab", { name: "用户管理" })).not.toBeInTheDocument();
    });
});

function tab(fullPath: string, title: string, affix = false) {
    return {
        fullPath,
        title,
        affix,
        cache: true,
        cacheName: `PageTab-${title}`,
        instanceId: 1,
        version: 0,
    };
}
