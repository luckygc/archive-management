import { createPinia, setActivePinia } from "pinia";
import type { RouteLocationNormalizedLoaded } from "vue-router";
import { beforeEach, describe, expect, it } from "vite-plus/test";

import { usePageTabsStore } from "./pageTabsStore";

describe("pageTabsStore", () => {
    beforeEach(() => setActivePinia(createPinia()));

    it("同一个路由组件可按 fullPath 建立多个独立页签实例", () => {
        const store = usePageTabsStore();
        store.ensureRouteTab(route("/archive/items?fonds=A"));
        store.ensureRouteTab(route("/archive/items?fonds=B"));

        expect(store.tabs.map((tab) => tab.fullPath)).toEqual([
            "/",
            "/archive/items?fonds=A",
            "/archive/items?fonds=B",
        ]);
    });

    it("刷新只递增当前页签实例的组件 key 版本", () => {
        const store = usePageTabsStore();
        store.ensureRouteTab(route("/archive/items?fonds=A"));
        store.ensureRouteTab(route("/archive/items?fonds=B"));

        store.refresh();

        expect(store.tabs.find((tab) => tab.fullPath.endsWith("A"))?.version).toBe(0);
        expect(store.tabs.find((tab) => tab.fullPath.endsWith("B"))?.version).toBe(1);
    });

    it("关闭活动页签后选择相邻实例且不允许关闭固定页签", () => {
        const store = usePageTabsStore();
        store.ensureRouteTab(route("/archive/items?fonds=A"));
        store.ensureRouteTab(route("/archive/items?fonds=B"));

        expect(store.close("/archive/items?fonds=B")).toBe("/archive/items?fonds=A");
        expect(store.close("/")).toBe("/archive/items?fonds=A");
        expect(store.tabs[0].fullPath).toBe("/");
    });

    it("关闭后重新打开相同 fullPath 时分配新的组件实例 key", () => {
        const store = usePageTabsStore();
        store.ensureRouteTab(route("/archive/items?fonds=A"));
        const oldInstanceId = store.tabs.at(-1)?.instanceId;
        store.close("/archive/items?fonds=A");

        store.ensureRouteTab(route("/archive/items?fonds=A"));

        expect(store.tabs.at(-1)?.instanceId).toBeGreaterThan(oldInstanceId ?? 0);
    });

    it("默认缓存并允许路由显式排除缓存", () => {
        const store = usePageTabsStore();
        store.ensureRouteTab(route("/archive/items"));
        store.ensureRouteTab(route("/intake", false));

        expect(store.tabs.find((tab) => tab.fullPath === "/archive/items")?.cache).toBe(true);
        expect(store.tabs.find((tab) => tab.fullPath === "/intake")?.cache).toBe(false);
    });
});

function route(fullPath: string, cache = true) {
    return {
        fullPath,
        meta: { title: "档案管理", cache },
    } as RouteLocationNormalizedLoaded;
}
