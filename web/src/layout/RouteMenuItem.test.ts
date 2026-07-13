import { cleanup, render, screen } from "@testing-library/vue";
import ElementPlus from "element-plus";
import type { RouteRecordRaw } from "vue-router";
import { afterEach, describe, expect, it } from "vite-plus/test";

import RouteMenuItem from "./RouteMenuItem.vue";

afterEach(cleanup);

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

        render(
            {
                components: { RouteMenuItem },
                setup: () => ({ routeRecord }),
                template: `<ElMenu><RouteMenuItem :route-record="routeRecord" /></ElMenu>`,
            },
            { global: { plugins: [ElementPlus] } },
        );

        expect(screen.getByText("一级")).toBeInTheDocument();
        expect(screen.getByText("二级")).toBeInTheDocument();
        expect(screen.getByText("三级")).toBeInTheDocument();
        expect(screen.getByText("四级")).toBeInTheDocument();
    });
});
