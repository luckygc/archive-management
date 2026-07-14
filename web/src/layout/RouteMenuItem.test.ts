import { cleanup, render, screen } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, getActivePinia, setActivePinia } from "pinia";
import type { RouteRecordRaw } from "vue-router";
import { afterEach, beforeEach, describe, expect, it } from "vite-plus/test";

import { usePermissionStore } from "@/stores/permissionStore";

import RouteMenuItem from "./RouteMenuItem.vue";

afterEach(cleanup);
beforeEach(() => setActivePinia(createPinia()));

describe("RouteMenuItem", () => {
    it("递归渲染任意层级的可见路由菜单", () => {
        const routeRecord: RouteRecordRaw = {
            path: "level-1",
            meta: { title: "一级", menu: true },
            children: [
                {
                    path: "level-2",
                    meta: { title: "二级", menu: true },
                    children: [
                        {
                            path: "level-3",
                            meta: { title: "三级", menu: true },
                            children: [
                                {
                                    path: "level-4",
                                    component: { template: "<div />" },
                                    meta: { title: "四级", menu: true },
                                },
                            ],
                        },
                    ],
                },
            ],
        };

        renderMenu(routeRecord);

        expect(screen.getByText("一级")).toBeInTheDocument();
        expect(screen.getByText("二级")).toBeInTheDocument();
        expect(screen.getByText("三级")).toBeInTheDocument();
        expect(screen.getByText("四级")).toBeInTheDocument();
    });

    it("只渲染有权叶子并隐藏没有可访问子项的分组", () => {
        usePermissionStore().permissionCodes = ["archive:item:read"];
        const routeRecord: RouteRecordRaw = {
            path: "root",
            meta: { title: "根菜单", menu: true },
            children: [
                {
                    path: "archive",
                    meta: { title: "档案管理", menu: true },
                    children: [
                        {
                            path: "items",
                            component: {},
                            meta: {
                                title: "档案条目",
                                menu: true,
                                permission: "archive:item:read",
                            },
                        },
                    ],
                },
                {
                    path: "system",
                    meta: { title: "系统配置", menu: true },
                    children: [
                        {
                            path: "users",
                            component: {},
                            meta: {
                                title: "用户管理",
                                menu: true,
                                permission: "authentication:user:manage",
                            },
                        },
                    ],
                },
            ],
        };

        renderMenu(routeRecord);

        expect(screen.getByText("档案管理")).toBeInTheDocument();
        expect(screen.getByText("档案条目")).toBeInTheDocument();
        expect(screen.queryByText("系统配置")).not.toBeInTheDocument();
        expect(screen.queryByText("用户管理")).not.toBeInTheDocument();
    });
});

function renderMenu(routeRecord: RouteRecordRaw) {
    const pinia = getActivePinia()!;
    return render(
        {
            components: { RouteMenuItem },
            setup: () => ({ routeRecord }),
            template: `<ElMenu><RouteMenuItem :route-record="routeRecord" /></ElMenu>`,
        },
        { global: { plugins: [ElementPlus, pinia] } },
    );
}
