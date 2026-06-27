import type { AppRouteHandle } from "@/app/routes";

export interface PageTab {
    fullPath: string;
    title: string;
    keepAlive: boolean;
    affix: boolean;
}

export function createPageTab(fullPath: string, handle: AppRouteHandle | undefined) {
    if (!handle?.title) {
        return undefined;
    }
    return {
        fullPath,
        title: handle.title,
        keepAlive: handle.keepAlive === true,
        affix: handle.affixTab === true,
    } satisfies PageTab;
}

export function closeTab(tabs: PageTab[], fullPath: string, activeFullPath: string) {
    const index = tabs.findIndex((tab) => tab.fullPath === fullPath);
    if (index < 0 || tabs[index].affix) {
        return {
            activeFullPath,
            tabs,
        };
    }

    const nextTabs = tabs.filter((tab) => tab.fullPath !== fullPath);
    if (activeFullPath !== fullPath) {
        return {
            activeFullPath,
            tabs: nextTabs,
        };
    }

    const nextActive = nextTabs[index] ?? nextTabs[index - 1] ?? nextTabs[0];
    return {
        activeFullPath: nextActive?.fullPath ?? "/",
        tabs: nextTabs,
    };
}

export function closeOtherTabs(tabs: PageTab[], activeFullPath: string) {
    return tabs.filter((tab) => tab.affix || tab.fullPath === activeFullPath);
}

export function closeLeftTabs(tabs: PageTab[], activeFullPath: string) {
    const index = tabs.findIndex((tab) => tab.fullPath === activeFullPath);
    if (index < 0) {
        return tabs;
    }
    return tabs.filter((tab, tabIndex) => tab.affix || tabIndex >= index);
}

export function closeRightTabs(tabs: PageTab[], activeFullPath: string) {
    const index = tabs.findIndex((tab) => tab.fullPath === activeFullPath);
    if (index < 0) {
        return tabs;
    }
    return tabs.filter((tab, tabIndex) => tab.affix || tabIndex <= index);
}

export function closeAllTabs(tabs: PageTab[]) {
    return tabs.filter((tab) => tab.affix);
}
