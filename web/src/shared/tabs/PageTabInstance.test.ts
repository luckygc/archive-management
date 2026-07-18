import { defineComponent, markRaw, nextTick, onUnmounted } from "vue";
import { cleanup, render, screen, waitFor } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, describe, expect, it } from "vite-plus/test";

import { usePageTabsStore } from "@/stores/pageTabsStore";
import PageTabCache from "./PageTabCache.vue";

afterEach(cleanup);

describe("PageTabInstance", () => {
    it("按页签实例保活并在关闭页签时销毁", async () => {
        let mountCount = 0;
        let unmountCount = 0;
        const Page = markRaw(
            defineComponent({
                setup() {
                    const instance = ++mountCount;
                    onUnmounted(() => unmountCount++);
                    return { instance };
                },
                template: "<div>页面实例 {{ instance }}</div>",
            }),
        );
        const pinia = createPinia();
        setActivePinia(pinia);
        const router = testRouter(Page);
        await router.push("/");
        await router.isReady();
        render(cacheHarness(Page), { global: { plugins: [pinia, router] } });
        expect(screen.getByText("页面实例 1")).toBeInTheDocument();

        const store = usePageTabsStore();
        await router.push("/items?fonds=B");
        await nextTick();
        expect(screen.getByText("页面实例 2")).toBeInTheDocument();
        expect(screen.queryByText("页面实例 1")).not.toBeInTheDocument();

        await router.push("/");
        await nextTick();
        expect(screen.getByText("页面实例 1")).toBeInTheDocument();
        expect(mountCount).toBe(2);

        await router.push("/items?fonds=B");
        await nextTick();

        store.close("/items?fonds=B");
        await router.push("/");
        await nextTick();
        expect(screen.getByText("页面实例 1")).toBeInTheDocument();
        expect(screen.queryByText("页面实例 2")).not.toBeInTheDocument();
        expect(mountCount).toBe(2);
        expect(unmountCount).toBe(1);
    });

    it("递增版本号会重新挂载当前页签组件", async () => {
        let mountCount = 0;
        const Page = markRaw(
            defineComponent({
                setup() {
                    const instance = ++mountCount;
                    return { instance };
                },
                template: "<div>刷新实例 {{ instance }}</div>",
            }),
        );
        const pinia = createPinia();
        setActivePinia(pinia);
        const router = testRouter(Page);
        await router.push("/");
        await router.isReady();
        render(cacheHarness(Page), { global: { plugins: [pinia, router] } });

        usePageTabsStore().refresh("/");

        await waitFor(() => expect(screen.getByText("刷新实例 2")).toBeInTheDocument());
        expect(mountCount).toBe(2);
    });

    it("只保活最近八个页签实例", async () => {
        let mountCount = 0;
        const Page = markRaw(
            defineComponent({
                setup() {
                    const instance = ++mountCount;
                    return { instance };
                },
                template: "<div>缓存实例 {{ instance }}</div>",
            }),
        );
        const pinia = createPinia();
        setActivePinia(pinia);
        const router = testRouter(Page);
        await router.push("/");
        await router.isReady();
        render(cacheHarness(Page), { global: { plugins: [pinia, router] } });

        for (let index = 1; index <= 8; index++) {
            await router.push(`/items?fonds=${index}`);
            await nextTick();
        }
        expect(mountCount).toBe(9);

        await router.push("/");
        await nextTick();

        expect(mountCount).toBe(10);
    });
});

function testRouter(component: ReturnType<typeof defineComponent>) {
    return createRouter({
        history: createMemoryHistory(),
        routes: [
            { path: "/", component, meta: { title: "工作台", affixTab: true, cache: true } },
            { path: "/items", component, meta: { title: "档案管理", cache: true } },
        ],
    });
}

function cacheHarness(page: ReturnType<typeof defineComponent>) {
    return defineComponent({
        components: { PageTabCache },
        setup() {
            return { page };
        },
        template: `<PageTabCache :active-component="page" :route="$route" />`,
    });
}
