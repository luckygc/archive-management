import { describe, expect, it } from "vite-plus/test";

import { normalizeRedirect, router, workspaceRoutes } from "./routes";

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

    it("在路由边界清洗登录后跳转地址", () => {
        expect(normalizeRedirect("/archive/items?fonds=A")).toBe("/archive/items?fonds=A");
        expect(normalizeRedirect("//outside.example")).toBe("/");
        expect(normalizeRedirect("https://outside.example")).toBe("/");
        expect(normalizeRedirect(["/archive/items"])).toBe("/");
    });
});
