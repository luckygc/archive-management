import type { Component } from "vue";
import type { RouteLocationNormalized, RouteMeta, RouteRecordRaw } from "vue-router";
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
    FolderOpened,
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
        permission?: string;
        permissionsAnyOf?: string[];
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
        { permission: "archive:item:read" },
    ),
    route(
        "archive/items",
        "archive-items",
        "档案管理",
        Folder,
        () => import("@/pages/archive-items/ArchiveItemManagementPage.vue"),
        { permission: "archive:item:read" },
    ),
    route(
        "archive/volumes",
        "archive-volumes",
        "案卷管理",
        FolderOpened,
        () => import("@/pages/archive-volumes/ArchiveVolumesPage.vue"),
        { permission: "archive:item:read" },
    ),
    group("archive/catalog", "目录配置", Collection, [
        route(
            "fonds",
            "archive-fonds",
            "全宗管理",
            OfficeBuilding,
            () => import("@/pages/archive-fonds/ArchiveFondsPage.vue"),
            { permission: "archive:metadata:manage" },
        ),
        route(
            "categories",
            "archive-categories",
            "档案分类",
            Collection,
            () => import("@/pages/archive-categories/ArchiveCategoriesPage.vue"),
            { permission: "archive:metadata:manage" },
        ),
    ]),
    route(
        "intake",
        "intake",
        "归档接收",
        UploadFilled,
        () => import("@/pages/intake/IntakePage.vue"),
        { cache: false, menu: false },
    ),
    group("archive/governance", "档案治理", DataAnalysis, [
        route(
            "schemes",
            "archive-governance",
            "治理方案",
            DataAnalysis,
            () => import("@/pages/archive-governance/ArchiveGovernancePage.vue"),
            { permission: "archive:governance:manage" },
        ),
        route(
            "ontology",
            "archive-ontology",
            "本体管理",
            Aim,
            () => import("@/pages/archive-ontology/ArchiveOntologyPage.vue"),
            { permission: "archive:governance:manage" },
        ),
        route(
            "rules",
            "archive-rules",
            "本地规则",
            Key,
            () => import("@/pages/archive-rules/ArchiveRulesPage.vue"),
            { permission: "archive:governance:manage" },
        ),
        route(
            "rule-traces",
            "archive-rule-traces",
            "规则追踪",
            Document,
            () => import("@/pages/archive-rule-traces/ArchiveRuleTracesPage.vue"),
            { permission: "archive:governance:manage" },
        ),
    ]),
    group("system", "系统配置", Setting, [
        route(
            "users",
            "authentication-users",
            "用户管理",
            User,
            () => import("@/pages/authentication-users/AuthenticationUsersPage.vue"),
            { permission: "authentication:user:manage" },
        ),
        route(
            "authorization",
            "authorization-management",
            "授权管理",
            Lock,
            () => import("@/pages/authorization-management/AuthorizationManagementPage.vue"),
            {
                permissionsAnyOf: ["authorization:permission:manage", "archive:data-scope:manage"],
            },
        ),
        route(
            "roles",
            "authorization-roles",
            "角色管理",
            UserFilled,
            () => import("@/pages/authorization-roles/AuthorizationRolesPage.vue"),
            { permission: "authorization:role:manage" },
        ),
        route(
            "data-scopes",
            "archive-data-scopes",
            "数据范围",
            Box,
            () => import("@/pages/archive-data-scopes/ArchiveDataScopesPage.vue"),
            { permission: "archive:data-scope:manage" },
        ),
        route(
            "organization-departments",
            "organization-departments",
            "组织架构",
            OfficeBuilding,
            () => import("@/pages/organization-departments/OrganizationDepartmentsPage.vue"),
            { permission: "organization:department:manage" },
        ),
        route(
            "storage",
            "storage",
            "存储配置",
            Files,
            () => import("@/pages/placeholder/PlaceholderPage.vue"),
            { cache: false, menu: false, pageTitle: "存储配置" },
        ),
        route(
            "login-sessions",
            "login-sessions",
            "登录会话",
            Monitor,
            () => import("@/pages/login-sessions/LoginSessionsPage.vue"),
            { permission: "authentication:session:manage" },
        ),
        route(
            "authentication-events",
            "authentication-events",
            "认证审计",
            Avatar,
            () => import("@/pages/authentication-events/AuthenticationEventsPage.vue"),
            { permission: "authentication:audit:read" },
        ),
        route(
            "settings",
            "settings",
            "系统参数",
            Setting,
            () => import("@/pages/placeholder/PlaceholderPage.vue"),
            { cache: false, menu: false, pageTitle: "系统参数" },
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
            path: "/forbidden",
            name: "forbidden",
            component: () => import("@/pages/forbidden/ForbiddenPage.vue"),
            meta: { title: "无访问权限", menu: false, cache: false },
        },
        {
            path: "/",
            component: () => import("@/layout/AppShell.vue"),
            children: workspaceRoutes,
        },
    ],
});

router.beforeEach(navigationGuard);

export async function navigationGuard(to: Pick<RouteLocationNormalized, "fullPath" | "meta">) {
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
    try {
        await permissionStore.ensureFresh();
    } catch {
        return {
            path: "/authentication-error",
            query: { redirect: to.fullPath },
            replace: true,
        };
    }
    if (!hasRoutePermission(to.meta, permissionStore)) {
        return { name: "forbidden", replace: true };
    }
    return true;
}

export interface PermissionChecker {
    has(code: string): boolean;
}

export function hasRoutePermission(
    meta: Pick<RouteMeta, "permission" | "permissionsAnyOf">,
    permission: PermissionChecker,
) {
    if (meta.permission && !permission.has(meta.permission)) return false;
    return !(
        meta.permissionsAnyOf?.length && !meta.permissionsAnyOf.some((code) => permission.has(code))
    );
}

export function canAccessRoute(record: RouteRecordRaw, permission: PermissionChecker): boolean {
    if (!hasRoutePermission(record.meta ?? {}, permission)) return false;
    const menuChildren = (record.children ?? []).filter((child) => child.meta?.menu === true);
    if (menuChildren.length > 0)
        return menuChildren.some((child) => canAccessRoute(child, permission));
    return !(
        record.meta?.menu === true &&
        record.children !== undefined &&
        record.component == null
    );
}

function route(
    path: string,
    name: string,
    title: string,
    icon: Component,
    component: () => Promise<unknown>,
    extra: {
        affixTab?: boolean;
        cache?: boolean;
        menu?: boolean;
        pageTitle?: string;
        permission?: string;
        permissionsAnyOf?: string[];
    } = {},
): RouteRecordRaw {
    const {
        affixTab = false,
        cache = true,
        menu = true,
        permission,
        permissionsAnyOf,
        ...props
    } = extra;
    return {
        path,
        name,
        component,
        props,
        meta: { title, icon, menu, affixTab, cache, permission, permissionsAnyOf },
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
