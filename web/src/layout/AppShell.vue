<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from "vue";
import { isNavigationFailure, useRouter } from "vue-router";
import type { TabsPaneContext, TabPaneName } from "element-plus";
import { Collection, Refresh, User } from "@element-plus/icons-vue";

import {
    canAccessRoute,
    hasRoutePermission,
    navigationPending,
    workspaceRoutes,
} from "@/app/routes";
import RouteMenuItem from "@/layout/RouteMenuItem.vue";
import ForbiddenPage from "@/pages/forbidden/ForbiddenPage.vue";
import PageTabRouterView from "@/shared/tabs/PageTabRouterView.vue";
import { usePageTabsStore } from "@/stores/pageTabsStore";
import { PERMISSION_REFRESH_INTERVAL_MS, usePermissionStore } from "@/stores/permissionStore";
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
const breadcrumbs = computed(() =>
    currentRoute.value.matched
        .filter((item) => item.meta.title && !item.meta.public)
        .map((item) => ({ title: item.meta.title!, path: item.path })),
);
const showInlineForbidden = computed(
    () =>
        permissionStore.ready &&
        currentRoute.value.name !== "forbidden" &&
        !hasRoutePermission(currentRoute.value.meta, permissionStore),
);
const showPermissionVerificationError = computed(
    () => permissionStore.initialized && !permissionStore.ready,
);
let permissionEffectSequence = 0;
let refreshTimer: number | undefined;
let permissionExpiryTimer: number | undefined;

watch(
    () => [permissionStore.snapshot.revision, currentRoute.value.fullPath] as const,
    () => {
        const effectId = ++permissionEffectSequence;
        if (!permissionStore.ready) return;
        const deniedFullPath = currentRoute.value.fullPath;
        const currentAllowed = hasRoutePermission(currentRoute.value.meta, permissionStore);
        tabsStore.removeInaccessible(
            (fullPath) => hasRoutePermission(router.resolve(fullPath).meta, permissionStore),
            currentAllowed ? undefined : deniedFullPath,
        );
        if (!currentAllowed && currentRoute.value.name !== "forbidden")
            void redirectDeniedRoute(effectId, permissionStore.snapshot.revision, deniedFullPath);
    },
    { immediate: true },
);

watch(
    () =>
        [
            permissionStore.snapshot.revision,
            permissionStore.snapshot.validUntil,
            permissionStore.snapshot.expired,
        ] as const,
    ([, validUntil, expired]) => {
        clearPermissionExpiryTimer();
        if (!permissionStore.initialized || expired || validUntil == null) return;
        permissionExpiryTimer = window.setTimeout(
            () => {
                permissionExpiryTimer = undefined;
                runScheduledPermissionRefresh();
            },
            Math.max(0, validUntil - Date.now()),
        );
    },
    { immediate: true },
);

onMounted(() => {
    refreshTimer = window.setInterval(
        runScheduledPermissionRefresh,
        PERMISSION_REFRESH_INTERVAL_MS,
    );
    window.addEventListener("focus", runScheduledPermissionRefresh);
    document.addEventListener("visibilitychange", refreshWhenVisible);
});

onUnmounted(() => {
    if (refreshTimer !== undefined) window.clearInterval(refreshTimer);
    clearPermissionExpiryTimer();
    window.removeEventListener("focus", runScheduledPermissionRefresh);
    document.removeEventListener("visibilitychange", refreshWhenVisible);
});

function clearPermissionExpiryTimer() {
    if (permissionExpiryTimer === undefined) return;
    window.clearTimeout(permissionExpiryTimer);
    permissionExpiryTimer = undefined;
}

function runScheduledPermissionRefresh() {
    void permissionStore.refreshIfNeeded().catch(() => undefined);
}

function refreshWhenVisible() {
    if (document.visibilityState === "visible") runScheduledPermissionRefresh();
}

async function retryPermissionVerification() {
    try {
        await permissionStore.ensureFresh();
    } catch {
        // 保持稳定的内联校验失败状态，允许用户再次重试。
    }
}

async function redirectDeniedRoute(effectId: number, revision: number, deniedFullPath: string) {
    try {
        const failure = await router.replace({ name: "forbidden" });
        if (
            effectId !== permissionEffectSequence ||
            revision !== permissionStore.snapshot.revision
        ) {
            await restoreStaleNavigation(deniedFullPath);
            return;
        }
        if (isNavigationFailure(failure)) return;
        tabsStore.removeInaccessible((fullPath) =>
            hasRoutePermission(router.resolve(fullPath).meta, permissionStore),
        );
    } catch {
        // 导航失败时保留当前页签，由内联 403 提供稳定的无权限状态。
    }
}

async function restoreStaleNavigation(fullPath: string) {
    if (
        currentRoute.value.name !== "forbidden" ||
        !hasRoutePermission(router.resolve(fullPath).meta, permissionStore)
    )
        return;
    try {
        await router.replace(fullPath);
    } catch {
        // 恢复导航失败时保持当前路由，下一次权限或用户导航会重新收敛状态。
    }
}

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
                        v-for="tab in tabsStore.tabs"
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
            <ElMain
                class="am-shell__content"
                :class="{ 'am-shell__content--full-bleed': currentRoute.meta.fullBleed }"
                :aria-busy="navigationPending"
            >
                <div
                    v-if="navigationPending"
                    class="am-shell__navigation-progress"
                    role="progressbar"
                    aria-label="正在打开页面"
                >
                    <span />
                </div>
                <section
                    v-if="showPermissionVerificationError"
                    class="am-page"
                    aria-labelledby="permission-verification-error-title"
                >
                    <ElResult
                        icon="error"
                        title="权限校验失败"
                        sub-title="当前无法验证账号权限，已暂停显示受保护内容。请检查网络后重试。"
                    >
                        <template #title>
                            <h1 id="permission-verification-error-title">权限校验失败</h1>
                        </template>
                        <template #extra>
                            <ElButton type="primary" @click="retryPermissionVerification"
                                >重新校验权限</ElButton
                            >
                        </template>
                    </ElResult>
                </section>
                <ForbiddenPage v-else-if="showInlineForbidden" embedded />
                <PageTabRouterView v-else />
            </ElMain>
        </ElContainer>
    </ElContainer>
</template>
