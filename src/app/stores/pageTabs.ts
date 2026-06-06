import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { RouteLocationNormalizedLoaded } from "vue-router";

export interface PageTab {
  fullPath: string;
  title: string;
  routeName?: string;
  cacheName?: string;
  affix: boolean;
}

function readString(value: unknown) {
  return typeof value === "string" ? value : "";
}

function createTab(route: RouteLocationNormalizedLoaded): PageTab | null {
  if (route.name === "Login") {
    return null;
  }

  const title = readString(route.meta.title);
  if (!title) {
    return null;
  }

  const routeName = typeof route.name === "string" ? route.name : undefined;
  const cacheName = readString(route.meta.cacheName) || routeName;

  return {
    fullPath: route.fullPath,
    title,
    routeName,
    cacheName: route.meta.keepAlive === true ? cacheName : undefined,
    affix: route.meta.affixTab === true,
  };
}

export const usePageTabsStore = defineStore("pageTabs", () => {
  const tabs = ref<PageTab[]>([]);
  const activeFullPath = ref("");
  const refreshKey = ref(0);

  const cacheNames = computed(() =>
    tabs.value.map((item) => item.cacheName).filter((item): item is string => Boolean(item)),
  );

  function openRoute(route: RouteLocationNormalizedLoaded) {
    const tab = createTab(route);
    if (!tab) {
      return;
    }

    const exists = tabs.value.find((item) => item.fullPath === tab.fullPath);
    if (!exists) {
      tabs.value.push(tab);
    }
    activeFullPath.value = tab.fullPath;
  }

  function closeTab(fullPath: string) {
    const index = tabs.value.findIndex((item) => item.fullPath === fullPath);
    if (index < 0 || tabs.value[index].affix) {
      return activeFullPath.value;
    }

    const isActive = activeFullPath.value === fullPath;
    tabs.value.splice(index, 1);

    if (!isActive) {
      return activeFullPath.value;
    }

    const nextTab = tabs.value[index] ?? tabs.value[index - 1] ?? tabs.value[0];
    activeFullPath.value = nextTab?.fullPath ?? "/";
    return activeFullPath.value;
  }

  function closeOthers(fullPath: string) {
    tabs.value = tabs.value.filter((item) => item.affix || item.fullPath === fullPath);
    activeFullPath.value = fullPath;
  }

  function closeAll() {
    tabs.value = tabs.value.filter((item) => item.affix);
    activeFullPath.value = tabs.value[0]?.fullPath ?? "/";
    return activeFullPath.value;
  }

  function reset() {
    tabs.value = [];
    activeFullPath.value = "";
    refreshKey.value = 0;
  }

  function refreshCurrent() {
    refreshKey.value += 1;
  }

  return {
    tabs,
    activeFullPath,
    refreshKey,
    cacheNames,
    openRoute,
    closeTab,
    closeOthers,
    closeAll,
    refreshCurrent,
    reset,
  };
});
