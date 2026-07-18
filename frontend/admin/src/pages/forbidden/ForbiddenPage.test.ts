import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, describe, expect, it } from "vite-plus/test";

import ForbiddenPage from "./ForbiddenPage.vue";

afterEach(cleanup);

describe("ForbiddenPage", () => {
    it("向读屏用户说明无权原因并可返回工作台", async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [
                { path: "/", component: { template: "<div />" } },
                { path: "/forbidden", component: ForbiddenPage },
            ],
        });
        await router.push("/forbidden");
        await router.isReady();

        render(ForbiddenPage, { global: { plugins: [ElementPlus, router] } });

        expect(screen.getByRole("heading", { name: "没有访问权限" })).toBeInTheDocument();
        expect(screen.getByText(/请联系系统管理员/)).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "返回工作台" }));
        await waitFor(() => expect(router.currentRoute.value.fullPath).toBe("/"));
    });
});
