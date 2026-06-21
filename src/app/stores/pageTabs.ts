import { defineStore } from "pinia";
import { computed, nextTick, ref, shallowRef } from "vue";
import type { RouteLocationNormalizedLoaded } from "vue-router";

export interface PageTab {
  fullPath: string;
  title: string;
  routeName?: string;
  cacheName?: string;
  keepAlive: boolean;
  refreshVersion: number;
  pageComponentName: string;
  pageComponentKey: string;
  affix: boolean;
}

function readString(value: unknown) {
  return typeof value === "string" ? value : "";
}

function normalizeCacheSegment(value: string) {
  return value.replace(/[^A-Za-z0-9_]/g, "_") || "Route";
}

function hashFullPath(fullPath: string) {
  let hash = 5381;
  for (let index = 0; index < fullPath.length; index += 1) {
    hash = (hash * 33) ^ fullPath.charCodeAt(index);
  }
  return (hash >>> 0).toString(36);
}

function createCacheBaseName(routeName: string | undefined, fullPath: string) {
  return `PageTab_${normalizeCacheSegment(routeName || "Route")}_${hashFullPath(fullPath)}`;
}

function createTab(route: RouteLocationNormalizedLoaded): PageTab | null {
  const title = readString(route.meta.title);
  if (!title) {
    return null;
  }

  const routeName = typeof route.name === "string" ? route.name : undefined;
  const cacheName = readString(route.meta.cacheName) || routeName;
  const keepAlive = route.meta.keepAlive === true;
  const refreshVersion = 0;
  const pageComponentName = createCacheBaseName(cacheName, route.fullPath);

  return {
    fullPath: route.fullPath,
    title,
    routeName,
    cacheName: keepAlive ? cacheName : undefined,
    keepAlive,
    refreshVersion,
    pageComponentName,
    pageComponentKey: `${route.fullPath}:${refreshVersion}`,
    affix: route.meta.affixTab === true,
  };
}

export const usePageTabsStore = defineStore("pageTabs", () => {
  const tabs = shallowRef<PageTab[]>([]);
  const activeFullPath = ref("");
  const suspendedCacheName = ref("");
  const activeTab = computed(() =>
    tabs.value.find((item) => item.fullPath === activeFullPath.value),
  );

  const cacheNames = computed(() =>
    tabs.value
      .filter((item) => item.keepAlive)
      .map((item) => item.pageComponentName)
      .filter((item): item is string => Boolean(item) && item !== suspendedCacheName.value),
  );

  function ensureTabEntry(route: RouteLocationNormalizedLoaded) {
    activeFullPath.value = route.fullPath;

    const tab = createTab(route);
    if (!tab) {
      return;
    }

    const exists = tabs.value.find((item) => item.fullPath === tab.fullPath);
    if (!exists) {
      tabs.value = [...tabs.value, tab];
    } else {
      tabs.value = tabs.value.map((item) =>
        item.fullPath === tab.fullPath
          ? {
              ...item,
              title: tab.title,
              routeName: tab.routeName,
              cacheName: tab.cacheName,
              keepAlive: tab.keepAlive,
              affix: tab.affix,
            }
          : item,
      );
    }
  }

  function closeTab(fullPath: string) {
    const index = tabs.value.findIndex((item) => item.fullPath === fullPath);
    if (index < 0 || tabs.value[index].affix) {
      return activeFullPath.value;
    }

    const isActive = activeFullPath.value === fullPath;
    const nextTabs = tabs.value.filter((item) => item.fullPath !== fullPath);
    tabs.value = nextTabs;

    if (!isActive) {
      return activeFullPath.value;
    }

    const nextTab = nextTabs[index] ?? nextTabs[index - 1] ?? nextTabs[0];
    activeFullPath.value = nextTab?.fullPath ?? "/";
    return activeFullPath.value;
  }

  function resolveNextPathAfterClose(fullPath: string) {
    const index = tabs.value.findIndex((item) => item.fullPath === fullPath);
    if (index < 0 || tabs.value[index].affix) {
      return activeFullPath.value;
    }

    const nextTab = tabs.value[index + 1] ?? tabs.value[index - 1] ?? tabs.value[0];
    return nextTab?.fullPath ?? "/";
  }

  function closeOthers(fullPath: string) {
    tabs.value = tabs.value.filter((item) => item.affix || item.fullPath === fullPath);
    activeFullPath.value = fullPath;
    return activeFullPath.value;
  }

  function closeLeft(fullPath: string) {
    const index = tabs.value.findIndex((item) => item.fullPath === fullPath);
    if (index < 0) {
      return activeFullPath.value;
    }

    tabs.value = tabs.value.filter((item, itemIndex) => item.affix || itemIndex >= index);
    activeFullPath.value = fullPath;
    return activeFullPath.value;
  }

  function closeRight(fullPath: string) {
    const index = tabs.value.findIndex((item) => item.fullPath === fullPath);
    if (index < 0) {
      return activeFullPath.value;
    }

    tabs.value = tabs.value.filter((item, itemIndex) => item.affix || itemIndex <= index);
    activeFullPath.value = fullPath;
    return activeFullPath.value;
  }

  function closeAll() {
    tabs.value = tabs.value.filter((item) => item.affix);
    activeFullPath.value = tabs.value[0]?.fullPath ?? "/";
    return activeFullPath.value;
  }

  function reset() {
    tabs.value = [];
    activeFullPath.value = "";
    suspendedCacheName.value = "";
  }

  async function rerenderTab(tab: PageTab) {
    const refreshVersion = tab.refreshVersion + 1;
    tabs.value = tabs.value.map((item) =>
      item.fullPath === tab.fullPath
        ? {
            ...item,
            refreshVersion,
            pageComponentKey: `${item.fullPath}:${refreshVersion}`,
          }
        : item,
    );
    await nextTick();
  }

  async function refreshTab(fullPath: string) {
    const tab = tabs.value.find((item) => item.fullPath === fullPath);
    if (!tab) {
      return;
    }

    if (tab.keepAlive) {
      suspendedCacheName.value = tab.pageComponentName;
      await nextTick();
      await rerenderTab(tab);
      suspendedCacheName.value = "";
      await nextTick();
      return;
    }

    await rerenderTab(tab);
  }

  async function refreshCurrent() {
    await refreshTab(activeFullPath.value);
  }

  return {
    tabs,
    activeFullPath,
    activeTab,
    cacheNames,
    ensureTabEntry,
    resolveNextPathAfterClose,
    closeTab,
    closeOthers,
    closeLeft,
    closeRight,
    closeAll,
    refreshTab,
    refreshCurrent,
    reset,
  };
});
