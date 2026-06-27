import { create } from "zustand";

import type { AppRouteHandle } from "@/app/routes";

import {
    closeAllTabs,
    closeLeftTabs,
    closeOtherTabs,
    closeRightTabs,
    closeTab,
    createPageTab,
} from "./pageTabs";
import type { PageTab } from "./pageTabs";

export const dashboardTab: PageTab = {
    fullPath: "/",
    title: "工作台",
    keepAlive: true,
    affix: true,
};

interface PageTabsState {
    tabs: PageTab[];
    activeFullPath: string;
    ensureRouteTab: (fullPath: string, routeHandle: AppRouteHandle | undefined) => void;
    closeTab: (fullPath: string) => { tabs: PageTab[]; activeFullPath: string };
    closeOtherTabs: () => PageTab[];
    closeLeftTabs: () => PageTab[];
    closeRightTabs: () => PageTab[];
    closeAllTabs: () => PageTab[];
    reset: () => void;
}

const initialState = {
    tabs: [dashboardTab],
    activeFullPath: "/",
};

export const usePageTabsStore = create<PageTabsState>((set, get) => ({
    ...initialState,
    ensureRouteTab: (fullPath, routeHandle) => {
        const tab = createPageTab(fullPath, routeHandle);
        if (!tab) {
            return;
        }
        set((state) => ({
            activeFullPath: fullPath,
            tabs: state.tabs.some((item) => item.fullPath === tab.fullPath)
                ? state.tabs
                : [...state.tabs, tab],
        }));
    },
    closeTab: (fullPath) => {
        const next = closeTab(get().tabs, fullPath, get().activeFullPath);
        set(next);
        return next;
    },
    closeOtherTabs: () => {
        const activeFullPath = get().activeFullPath;
        const tabs = closeOtherTabs(get().tabs, activeFullPath);
        set({ activeFullPath, tabs });
        return tabs;
    },
    closeLeftTabs: () => {
        const activeFullPath = get().activeFullPath;
        const tabs = closeLeftTabs(get().tabs, activeFullPath);
        set({ activeFullPath, tabs });
        return tabs;
    },
    closeRightTabs: () => {
        const activeFullPath = get().activeFullPath;
        const tabs = closeRightTabs(get().tabs, activeFullPath);
        set({ activeFullPath, tabs });
        return tabs;
    },
    closeAllTabs: () => {
        const tabs = closeAllTabs(get().tabs);
        const activeFullPath = tabs[0]?.fullPath ?? "/";
        set({ activeFullPath, tabs });
        return tabs;
    },
    reset: () => set(initialState),
}));

export function resetPageTabsStore() {
    usePageTabsStore.getState().reset();
}
