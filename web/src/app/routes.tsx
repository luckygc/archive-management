import {
    ApartmentOutlined,
    AppstoreOutlined,
    DatabaseOutlined,
    FolderOpenOutlined,
    HomeOutlined,
    ImportOutlined,
    LockOutlined,
    SettingOutlined,
    UserOutlined,
} from "@ant-design/icons";
import type { ReactNode } from "react";
import { createHashRouter } from "react-router";

import { AppShell } from "@/layout/AppShell";
import { ArchiveCategoriesPage } from "@/pages/archive-categories/ArchiveCategoriesPage";
import { ArchiveFondsPage } from "@/pages/archive-fonds/ArchiveFondsPage";
import { ArchiveLibraryPage } from "@/pages/archive-library/ArchiveLibraryPage";
import { DashboardPage } from "@/pages/dashboard/DashboardPage";
import { IntakePage } from "@/pages/intake/IntakePage";
import { LoginPage } from "@/pages/login/LoginPage";
import { PlaceholderPage } from "@/pages/placeholder/PlaceholderPage";
import { AuthGate } from "@/shared/auth/AuthGate";

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
            element: <AuthGate />,
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
                                title: "档案库",
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
                            path: "system/users",
                            element: <PlaceholderPage title="用户管理" />,
                            handle: {
                                title: "用户管理",
                                icon: <UserOutlined />,
                                isMenu: true,
                            } satisfies AppRouteHandle,
                        },
                        {
                            path: "system/roles",
                            element: <PlaceholderPage title="角色管理" />,
                            handle: {
                                title: "角色管理",
                                icon: <LockOutlined />,
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
