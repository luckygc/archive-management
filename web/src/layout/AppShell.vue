<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import type { TabsPaneContext, TabPaneName } from "element-plus";
import { Collection, Refresh, User } from "@element-plus/icons-vue";

import { canAccessRoute, workspaceRoutes } from "@/app/routes";
import RouteMenuItem from "@/layout/RouteMenuItem.vue";
import PageTabRouterView from "@/shared/tabs/PageTabRouterView.vue";
import { usePageTabsStore } from "@/stores/pageTabsStore";
import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";

const router = useRouter();
const currentRoute = router.currentRoute;
const tabsStore = usePageTabsStore();
const sessionStore = useSessionStore();
const permissionStore = usePermissionStore();
const displayName = computed(() => sessionStore.currentUser?.displayName || "-");
const menuRoutes = computed(() =>
    workspaceRoutes.filter(
        (item) => item.meta?.menu === true && canAccessRoute(item, permissionStore),
    ),
);
const visibleTabs = computed(() =>
    tabsStore.tabs.filter((tab) => {
        const permission = router.resolve(tab.fullPath).meta.permission;
        return !permission || permissionStore.has(permission);
    }),
);
const breadcrumbs = computed(() =>
    currentRoute.value.matched
        .filter((item) => item.meta.title && !item.meta.public)
        .map((item) => ({ title: item.meta.title!, path: item.path })),
);

async function selectTab(tab: TabsPaneContext) {
    await router.push(String(tab.paneName));
}

async function closeTab(name: TabPaneName) {
    const nextPath = tabsStore.close(String(name));
    if (currentRoute.value.fullPath !== nextPath) await router.push(nextPath);
}

async function closeAll() {
    await router.push(tabsStore.closeAll());
}

async function logout() {
    await sessionStore.logoutCurrentUser();
    permissionStore.reset();
    tabsStore.reset();
    await router.replace("/login");
}
</script>

<template>
    <ElContainer class="am-shell">
        <ElAside class="am-shell__sider" width="232px">
            <div class="am-shell__brand">
                <ElIcon><Collection /></ElIcon>
                <strong>档案管理系统</strong>
            </div>
            <ElMenu :default-active="currentRoute.path" router>
                <RouteMenuItem
                    v-for="item in menuRoutes"
                    :key="String(item.name ?? item.path)"
                    :route-record="item"
                />
            </ElMenu>
        </ElAside>
        <ElContainer class="am-shell__workspace">
            <ElHeader class="am-shell__header" height="56px">
                <ElBreadcrumb separator="/">
                    <ElBreadcrumbItem
                        v-for="(item, index) in breadcrumbs"
                        :key="item.path"
                        :to="index < breadcrumbs.length - 1 ? item.path : undefined"
                        >{{ item.title }}</ElBreadcrumbItem
                    >
                </ElBreadcrumb>
                <ElDropdown
                    trigger="click"
                    @command="(command: string) => command === 'logout' && logout()"
                >
                    <ElButton text
                        ><ElIcon><User /></ElIcon>{{ displayName }}</ElButton
                    >
                    <template #dropdown
                        ><ElDropdownMenu
                            ><ElDropdownItem command="logout"
                                >退出登录</ElDropdownItem
                            ></ElDropdownMenu
                        ></template
                    >
                </ElDropdown>
            </ElHeader>
            <div class="am-shell__tabs">
                <ElTabs
                    :model-value="tabsStore.activeFullPath"
                    type="card"
                    @tab-click="selectTab"
                    @tab-remove="closeTab"
                >
                    <ElTabPane
                        v-for="tab in visibleTabs"
                        :key="tab.fullPath"
                        :name="tab.fullPath"
                        :label="tab.title"
                        :closable="!tab.affix"
                    />
                </ElTabs>
                <div class="am-shell__tab-actions">
                    <ElTooltip content="重新挂载当前页签"
                        ><ElButton
                            aria-label="刷新当前页签"
                            :icon="Refresh"
                            size="small"
                            @click="tabsStore.refresh()"
                    /></ElTooltip>
                    <ElButton size="small" text @click="tabsStore.closeOthers()">关闭其他</ElButton>
                    <ElButton size="small" text @click="tabsStore.closeSide('left')"
                        >关闭左侧</ElButton
                    >
                    <ElButton size="small" text @click="tabsStore.closeSide('right')"
                        >关闭右侧</ElButton
                    >
                    <ElButton size="small" text @click="closeAll">关闭全部</ElButton>
                </div>
            </div>
            <ElMain class="am-shell__content"><PageTabRouterView /></ElMain>
        </ElContainer>
    </ElContainer>
</template>
