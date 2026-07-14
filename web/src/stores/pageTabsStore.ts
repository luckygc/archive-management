import { ref } from "vue";
import { defineStore } from "pinia";
import type { RouteLocationNormalizedLoaded } from "vue-router";

export interface PageTab {
    fullPath: string;
    title: string;
    affix: boolean;
    cache: boolean;
    cacheName: string;
    instanceId: number;
    version: number;
}

const dashboardTab: PageTab = {
    fullPath: "/",
    title: "工作台",
    affix: true,
    cache: true,
    cacheName: "PageTab0",
    instanceId: 0,
    version: 0,
};

export const usePageTabsStore = defineStore("page-tabs", () => {
    const tabs = ref<PageTab[]>([{ ...dashboardTab }]);
    const activeFullPath = ref("/");
    let instanceSequence = 0;

    function ensureRouteTab(route: RouteLocationNormalizedLoaded) {
        if (!route.meta.title) return;
        activeFullPath.value = route.fullPath;
        if (tabs.value.some((tab) => tab.fullPath === route.fullPath)) return;
        const instanceId = ++instanceSequence;
        tabs.value.push({
            fullPath: route.fullPath,
            title: route.meta.title,
            affix: route.meta.affixTab === true,
            cache: route.meta.cache !== false,
            cacheName: `PageTab${instanceId}`,
            instanceId,
            version: 0,
        });
    }

    function refresh(fullPath = activeFullPath.value) {
        const tab = tabs.value.find((item) => item.fullPath === fullPath);
        if (tab) tab.version += 1;
    }

    function close(fullPath: string) {
        const index = tabs.value.findIndex((tab) => tab.fullPath === fullPath);
        if (index < 0 || tabs.value[index].affix) return activeFullPath.value;
        const wasActive = activeFullPath.value === fullPath;
        tabs.value.splice(index, 1);
        if (wasActive)
            activeFullPath.value =
                tabs.value[index]?.fullPath ?? tabs.value[index - 1]?.fullPath ?? "/";
        return activeFullPath.value;
    }

    function closeOthers() {
        tabs.value = tabs.value.filter((tab) => tab.affix || tab.fullPath === activeFullPath.value);
    }

    function closeSide(side: "left" | "right") {
        const activeIndex = tabs.value.findIndex((tab) => tab.fullPath === activeFullPath.value);
        tabs.value = tabs.value.filter(
            (tab, index) =>
                tab.affix || (side === "left" ? index >= activeIndex : index <= activeIndex),
        );
    }

    function closeAll() {
        tabs.value = tabs.value.filter((tab) => tab.affix);
        activeFullPath.value = tabs.value[0]?.fullPath ?? "/";
        return activeFullPath.value;
    }

    function removeInaccessible(canAccess: (fullPath: string) => boolean) {
        const activeWasRemoved = !canAccess(activeFullPath.value);
        tabs.value = tabs.value.filter((tab) => canAccess(tab.fullPath));
        if (tabs.value.length === 0) tabs.value = [{ ...dashboardTab }];
        if (!tabs.value.some((tab) => tab.fullPath === activeFullPath.value))
            activeFullPath.value = tabs.value[0].fullPath;
        return activeWasRemoved;
    }

    function reset() {
        tabs.value = [{ ...dashboardTab }];
        activeFullPath.value = "/";
    }

    return {
        tabs,
        activeFullPath,
        ensureRouteTab,
        refresh,
        close,
        closeOthers,
        closeSide,
        closeAll,
        removeInaccessible,
        reset,
    };
});
