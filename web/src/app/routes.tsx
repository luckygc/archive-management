import {
    ApartmentOutlined,
    AppstoreOutlined,
    DatabaseOutlined,
    FileSearchOutlined,
    FolderOpenOutlined,
    HomeOutlined,
    ImportOutlined,
    LockOutlined,
    MonitorOutlined,
    SafetyOutlined,
    SettingOutlined,
    TeamOutlined,
    UserOutlined,
} from "@ant-design/icons";
import type { ReactNode } from "react";
import { createHashRouter } from "react-router";

import { AppShell } from "@/layout/AppShell";
import { ArchiveCategoriesPage } from "@/pages/archive-categories/ArchiveCategoriesPage";
import { ArchiveDataScopesPage } from "@/pages/archive-data-scopes/ArchiveDataScopesPage";
import { ArchiveFondsPage } from "@/pages/archive-fonds/ArchiveFondsPage";
import { ArchiveItemManagementPage } from "@/pages/archive-items/ArchiveItemManagementPage";
import { ArchiveLibraryPage } from "@/pages/archive-library/ArchiveLibraryPage";
import { AuthenticationEventsPage } from "@/pages/authentication-events/AuthenticationEventsPage";
import { AuthenticationUsersPage } from "@/pages/authentication-users/AuthenticationUsersPage";
import { AuthorizationManagementPage } from "@/pages/authorization-management/AuthorizationManagementPage";
import { AuthorizationRolesPage } from "@/pages/authorization-roles/AuthorizationRolesPage";
import { DashboardPage } from "@/pages/dashboard/DashboardPage";
import { IntakePage } from "@/pages/intake/IntakePage";
import { LoginPage } from "@/pages/login/LoginPage";
import { LoginSessionsPage } from "@/pages/login-sessions/LoginSessionsPage";
import { OrganizationDepartmentsPage } from "@/pages/organization-departments/OrganizationDepartmentsPage";
import { PlaceholderPage } from "@/pages/placeholder/PlaceholderPage";
import { AuthenticationGate } from "@/shared/authentication/AuthenticationGate";

export interface AppRouteHandle {
    title: string;
    icon?: ReactNode;
    isMenu?: boolean;
    keepAlive?: boolean;
    affixTab?: boolean;
}

export function createAppRouter() {
    return createHashRouter([
        {
            path: "/login",
            element: <LoginPage />,
            handle: {
                title: "登录",
            } satisfies AppRouteHandle,
        },
        {
            path: "/",
            element: <AuthenticationGate />,
            children: [
                {
                    element: <AppShell />,
                    children: [
                        {
                            index: true,
                            element: <DashboardPage />,
                            handle: {
                                title: "工作台",
                                icon: <HomeOutlined />,
                                isMenu: true,
                                keepAlive: true,
                                affixTab: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "archive/library",
                            element: <ArchiveLibraryPage />,
                            handle: {
                                title: "档案搜索",
                                icon: <FileSearchOutlined />,
                                isMenu: true,
                                keepAlive: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "archive/items",
                            element: <ArchiveItemManagementPage />,
                            handle: {
                                title: "档案管理",
                                icon: <FolderOpenOutlined />,
                                isMenu: true,
                                keepAlive: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "archive/catalog/fonds",
                            element: <ArchiveFondsPage />,
                            handle: {
                                title: "全宗管理",
                                icon: <ApartmentOutlined />,
                                isMenu: true,
                                keepAlive: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "archive/catalog/categories",
                            element: <ArchiveCategoriesPage />,
                            handle: {
                                title: "档案分类",
                                icon: <AppstoreOutlined />,
                                isMenu: true,
                                keepAlive: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/data-scopes",
                            element: <ArchiveDataScopesPage />,
                            handle: {
                                title: "数据范围",
                                icon: <LockOutlined />,
                                isMenu: true,
                                keepAlive: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "intake",
                            element: <IntakePage />,
                            handle: {
                                title: "归档接收",
                                icon: <ImportOutlined />,
                                isMenu: true,
                                keepAlive: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/organization-departments",
                            element: <OrganizationDepartmentsPage />,
                            handle: {
                                title: "组织架构",
                                icon: <ApartmentOutlined />,
                                isMenu: true,
                                keepAlive: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/users",
                            element: <AuthenticationUsersPage />,
                            handle: {
                                title: "用户管理",
                                icon: <UserOutlined />,
                                isMenu: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/authorization",
                            element: <AuthorizationManagementPage />,
                            handle: {
                                title: "授权管理",
                                icon: <SafetyOutlined />,
                                isMenu: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/roles",
                            element: <AuthorizationRolesPage />,
                            handle: {
                                title: "角色管理",
                                icon: <TeamOutlined />,
                                isMenu: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/storage",
                            element: <PlaceholderPage title="存储配置" />,
                            handle: {
                                title: "存储配置",
                                icon: <DatabaseOutlined />,
                                isMenu: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/login-sessions",
                            element: <LoginSessionsPage />,
                            handle: {
                                title: "登录会话",
                                icon: <MonitorOutlined />,
                                isMenu: true,
                                keepAlive: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/authentication-events",
                            element: <AuthenticationEventsPage />,
                            handle: {
                                title: "认证审计",
                                icon: <LockOutlined />,
                                isMenu: true,
                                keepAlive: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/settings",
                            element: <PlaceholderPage title="系统参数" />,
                            handle: {
                                title: "系统参数",
                                icon: <SettingOutlined />,
                                isMenu: true,
                            } satisfies AppRouteHandle,
                        },
                    ],
                },
            ],
        },
    ]);
}
