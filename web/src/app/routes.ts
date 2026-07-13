import type { Component } from "vue";
import type { RouteRecordRaw } from "vue-router";
import { createRouter, createWebHashHistory } from "vue-router";

import {
    Aim,
    Avatar,
    Box,
    Collection,
    DataAnalysis,
    Document,
    Files,
    Folder,
    HomeFilled,
    UploadFilled,
    Key,
    Lock,
    Monitor,
    OfficeBuilding,
    Search,
    Setting,
    User,
    UserFilled,
} from "@element-plus/icons-vue";

import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";

declare module "vue-router" {
    interface RouteMeta {
        title?: string;
        icon?: Component;
        menu?: boolean;
        affixTab?: boolean;
        cache?: boolean;
        public?: boolean;
    }
}

export const workspaceRoutes: RouteRecordRaw[] = [
    route(
        "",
        "dashboard",
        "工作台",
        HomeFilled,
        () => import("@/pages/dashboard/DashboardPage.vue"),
        {
            affixTab: true,
        },
    ),
    route(
        "archive/library",
        "archive-library",
        "档案搜索",
        Search,
        () => import("@/pages/archive-library/ArchiveLibraryPage.vue"),
    ),
    route(
        "archive/items",
        "archive-items",
        "档案管理",
        Folder,
        () => import("@/pages/archive-items/ArchiveItemManagementPage.vue"),
    ),
    group("archive/catalog", "目录配置", Collection, [
        route(
            "fonds",
            "archive-fonds",
            "全宗管理",
            OfficeBuilding,
            () => import("@/pages/archive-fonds/ArchiveFondsPage.vue"),
        ),
        route(
            "categories",
            "archive-categories",
            "档案分类",
            Collection,
            () => import("@/pages/archive-categories/ArchiveCategoriesPage.vue"),
        ),
    ]),
    route(
        "intake",
        "intake",
        "归档接收",
        UploadFilled,
        () => import("@/pages/intake/IntakePage.vue"),
        { cache: false },
    ),
    group("archive/governance", "档案治理", DataAnalysis, [
        route(
            "schemes",
            "archive-governance",
            "治理方案",
            DataAnalysis,
            () => import("@/pages/archive-governance/ArchiveGovernancePage.vue"),
        ),
        route(
            "ontology",
            "archive-ontology",
            "本体管理",
            Aim,
            () => import("@/pages/archive-ontology/ArchiveOntologyPage.vue"),
        ),
        route(
            "rules",
            "archive-rules",
            "本地规则",
            Key,
            () => import("@/pages/archive-rules/ArchiveRulesPage.vue"),
        ),
        route(
            "rule-traces",
            "archive-rule-traces",
            "规则追踪",
            Document,
            () => import("@/pages/archive-rule-traces/ArchiveRuleTracesPage.vue"),
        ),
    ]),
    group("system", "系统配置", Setting, [
        route(
            "users",
            "authentication-users",
            "用户管理",
            User,
            () => import("@/pages/authentication-users/AuthenticationUsersPage.vue"),
        ),
        route(
            "authorization",
            "authorization-management",
            "授权管理",
            Lock,
            () => import("@/pages/authorization-management/AuthorizationManagementPage.vue"),
        ),
        route(
            "roles",
            "authorization-roles",
            "角色管理",
            UserFilled,
            () => import("@/pages/authorization-roles/AuthorizationRolesPage.vue"),
        ),
        route(
            "data-scopes",
            "archive-data-scopes",
            "数据范围",
            Box,
            () => import("@/pages/archive-data-scopes/ArchiveDataScopesPage.vue"),
        ),
        route(
            "organization-departments",
            "organization-departments",
            "组织架构",
            OfficeBuilding,
            () => import("@/pages/organization-departments/OrganizationDepartmentsPage.vue"),
        ),
        route(
            "storage",
            "storage",
            "存储配置",
            Files,
            () => import("@/pages/placeholder/PlaceholderPage.vue"),
            { cache: false, pageTitle: "存储配置" },
        ),
        route(
            "login-sessions",
            "login-sessions",
            "登录会话",
            Monitor,
            () => import("@/pages/login-sessions/LoginSessionsPage.vue"),
        ),
        route(
            "authentication-events",
            "authentication-events",
            "认证审计",
            Avatar,
            () => import("@/pages/authentication-events/AuthenticationEventsPage.vue"),
        ),
        route(
            "settings",
            "settings",
            "系统参数",
            Setting,
            () => import("@/pages/placeholder/PlaceholderPage.vue"),
            { cache: false, pageTitle: "系统参数" },
        ),
    ]),
];

export const router = createRouter({
    history: createWebHashHistory(),
    routes: [
        {
            path: "/login",
            name: "login",
            component: () => import("@/pages/login/LoginPage.vue"),
            props: (route) => ({ redirect: normalizeRedirect(route.query.redirect) }),
            meta: { title: "登录", public: true },
        },
        {
            path: "/authentication-error",
            name: "authentication-error",
            component: () => import("@/pages/login/AuthenticationErrorPage.vue"),
            props: (route) => ({ redirect: normalizeRedirect(route.query.redirect) }),
            meta: { title: "会话校验失败", public: true },
        },
        {
            path: "/",
            component: () => import("@/layout/AppShell.vue"),
            children: workspaceRoutes,
        },
    ],
});

router.beforeEach(async (to) => {
    if (to.meta.public) {
        return true;
    }

    const sessionStore = useSessionStore();
    if (!sessionStore.initialized) {
        try {
            await sessionStore.fetchCurrentUser();
        } catch {
            return {
                path: "/authentication-error",
                query: { redirect: to.fullPath },
                replace: true,
            };
        }
    }
    if (!sessionStore.currentUser) {
        return { path: "/login", query: { redirect: to.fullPath }, replace: true };
    }

    const permissionStore = usePermissionStore();
    if (!permissionStore.initialized) {
        await permissionStore.fetchSummary().catch(() => undefined);
    }
    return true;
});

function route(
    path: string,
    name: string,
    title: string,
    icon: Component,
    component: () => Promise<unknown>,
    extra: { affixTab?: boolean; cache?: boolean; menu?: boolean; pageTitle?: string } = {},
): RouteRecordRaw {
    const { affixTab = false, cache = true, menu = true, ...props } = extra;
    return {
        path,
        name,
        component,
        props,
        meta: { title, icon, menu, affixTab, cache },
    } as RouteRecordRaw;
}

function group(
    path: string,
    title: string,
    icon: Component,
    children: RouteRecordRaw[],
): RouteRecordRaw {
    return { path, children, meta: { title, icon, menu: true, cache: false } };
}

export function normalizeRedirect(value: unknown) {
    return typeof value === "string" && value.startsWith("/") && !value.startsWith("//")
        ? value
        : "/";
}
