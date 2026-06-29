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
    ReloadOutlined,
    SettingOutlined,
    UserOutlined,
} from "@ant-design/icons";
import { Button, Dropdown, Layout, Menu, Space, Tabs, Tooltip, Typography } from "antd";
import { useSessionStore } from "@archive-management/frontend-core/auth";
import type { MenuProps } from "antd";
import { useKeepAliveRef } from "keepalive-for-react";
import KeepAliveRouteOutlet from "keepalive-for-react-router";
import { Suspense, useEffect, useMemo } from "react";
import type { ReactNode } from "react";
import { Link, useLocation, useMatches, useNavigate } from "react-router";

import type { AppRouteHandle } from "@/app/routes";
import type { PageTab } from "@/shared/tabs/pageTabs";
import { usePageTabsStore } from "@/shared/tabs/pageTabsStore";

const { Content, Header, Sider } = Layout;

const menuItems: MenuProps["items"] = [
    { key: "/", label: <Link to="/">工作台</Link>, icon: <HomeOutlined /> },
    {
        key: "/archive/library",
        label: <Link to="/archive/library">档案搜索</Link>,
        icon: <FileSearchOutlined />,
    },
    {
        key: "/archive/items",
        label: <Link to="/archive/items">档案管理</Link>,
        icon: <FolderOpenOutlined />,
    },
    {
        key: "/archive/catalog",
        label: "目录配置",
        icon: <AppstoreOutlined />,
        children: [
            {
                key: "/archive/catalog/fonds",
                label: <Link to="/archive/catalog/fonds">全宗管理</Link>,
                icon: <ApartmentOutlined />,
            },
            {
                key: "/archive/catalog/categories",
                label: <Link to="/archive/catalog/categories">档案分类</Link>,
                icon: <AppstoreOutlined />,
            },
        ],
    },
    {
        key: "/intake",
        label: <Link to="/intake">归档接收</Link>,
        icon: <ImportOutlined />,
    },
    {
        key: "/system",
        label: "系统配置",
        icon: <SettingOutlined />,
        children: [
            {
                key: "/system/users",
                label: <Link to="/system/users">用户管理</Link>,
                icon: <UserOutlined />,
            },
            {
                key: "/system/roles",
                label: <Link to="/system/roles">角色管理</Link>,
                icon: <LockOutlined />,
            },
            {
                key: "/system/storage",
                label: <Link to="/system/storage">存储配置</Link>,
                icon: <DatabaseOutlined />,
            },
            {
                key: "/system/login-sessions",
                label: <Link to="/system/login-sessions">登录会话</Link>,
                icon: <MonitorOutlined />,
            },
            {
                key: "/system/authentication-events",
                label: <Link to="/system/authentication-events">认证审计</Link>,
                icon: <LockOutlined />,
            },
        ],
    },
];

export function AppShell() {
    const location = useLocation();
    const matches = useMatches();
    const navigate = useNavigate();
    const aliveRef = useKeepAliveRef();
    const activeFullPath = `${location.pathname}${location.search}${location.hash}`;
    const tabs = usePageTabsStore((state) => state.tabs);
    const ensureRouteTab = usePageTabsStore((state) => state.ensureRouteTab);
    const closePageTab = usePageTabsStore((state) => state.closeTab);
    const closeOtherPageTabs = usePageTabsStore((state) => state.closeOtherTabs);
    const closeLeftPageTabs = usePageTabsStore((state) => state.closeLeftTabs);
    const closeRightPageTabs = usePageTabsStore((state) => state.closeRightTabs);
    const closeAllPageTabs = usePageTabsStore((state) => state.closeAllTabs);
    const currentUser = useSessionStore((state) => state.currentUser);
    const logoutCurrentUser = useSessionStore((state) => state.logoutCurrentUser);
    const routeHandle = activeRouteHandle(matches);

    useEffect(() => {
        ensureRouteTab(activeFullPath, routeHandle);
    }, [activeFullPath, routeHandle, ensureRouteTab]);

    const excludedCacheKeys = useMemo(
        () => tabs.filter((tab) => !tab.keepAlive).map((tab) => tab.fullPath),
        [tabs],
    );

    async function go(path: string) {
        await navigate(path);
    }

    async function closeOne(tab: PageTab) {
        const next = closePageTab(tab.fullPath);
        await aliveRef.current?.destroy(tab.fullPath);
        if (tab.fullPath === activeFullPath) {
            await go(next.activeFullPath || "/");
        }
    }

    function refreshCurrent() {
        aliveRef.current?.refresh(activeFullPath);
    }

    async function closeOthers() {
        closeOtherPageTabs();
        await aliveRef.current?.destroyOther(activeFullPath);
    }

    async function closeSide(which: "left" | "right") {
        const nextTabs = which === "left" ? closeLeftPageTabs() : closeRightPageTabs();
        await destroyRemovedTabs(tabs, nextTabs, aliveRef.current?.destroy);
    }

    async function closeAll() {
        const nextTabs = closeAllPageTabs();
        const nextPath = nextTabs[0]?.fullPath ?? "/";
        await aliveRef.current?.destroyAll();
        await go(nextPath);
    }

    async function logout() {
        await logoutCurrentUser();
        await go("/login");
    }

    return (
        <Layout className="am-shell">
            <Sider className="am-shell__sider" width={232}>
                <div className="am-shell__brand">
                    <AppstoreOutlined />
                    <Typography.Text strong>档案管理系统</Typography.Text>
                </div>
                <Menu
                    defaultOpenKeys={["/archive/catalog", "/system"]}
                    items={menuItems}
                    mode="inline"
                    selectedKeys={[location.pathname]}
                />
            </Sider>
            <Layout>
                <Header className="am-shell__header">
                    <Space size={8}>
                        <Dropdown
                            menu={{
                                items: [{ key: "logout", label: "退出登录" }],
                                onClick: () => void logout(),
                            }}
                            trigger={["click"]}
                        >
                            <Button icon={<UserOutlined />} type="text">
                                {currentUser?.displayName ?? currentUser?.username}
                            </Button>
                        </Dropdown>
                    </Space>
                </Header>
                <div className="am-shell__tabs">
                    <Tabs
                        activeKey={activeFullPath}
                        items={tabs.map((tab) => ({
                            key: tab.fullPath,
                            label: <TabLabel tab={tab} onClose={() => void closeOne(tab)} />,
                        }))}
                        tabBarExtraContent={{
                            right: (
                                <Space size={4}>
                                    <Tooltip title="重新挂载当前页签">
                                        <Button
                                            aria-label="刷新当前页签"
                                            icon={<ReloadOutlined />}
                                            size="small"
                                            onClick={refreshCurrent}
                                        />
                                    </Tooltip>
                                    <Button
                                        size="small"
                                        type="text"
                                        onClick={() => void closeOthers()}
                                    >
                                        关闭其他
                                    </Button>
                                    <Button
                                        size="small"
                                        type="text"
                                        onClick={() => void closeSide("left")}
                                    >
                                        关闭左侧
                                    </Button>
                                    <Button
                                        size="small"
                                        type="text"
                                        onClick={() => void closeSide("right")}
                                    >
                                        关闭右侧
                                    </Button>
                                    <Button
                                        size="small"
                                        type="text"
                                        onClick={() => void closeAll()}
                                    >
                                        关闭全部
                                    </Button>
                                </Space>
                            ),
                        }}
                        type="card"
                        onChange={(path) => void go(path)}
                    />
                </div>
                <Content className="am-shell__content">
                    <KeepAliveRouteOutlet
                        activeCacheKey={activeFullPath}
                        aliveRef={aliveRef}
                        cacheNodeClassName="am-shell__cache-node"
                        containerClassName="am-shell__cache"
                        exclude={excludedCacheKeys}
                        max={12}
                        wrapperComponent={PageSuspense}
                    />
                </Content>
            </Layout>
        </Layout>
    );
}

function PageSuspense({ children }: { children: ReactNode }) {
    return (
        <Suspense fallback={<div className="am-page-loading">页面加载中</div>}>{children}</Suspense>
    );
}

function TabLabel({ tab, onClose }: { tab: PageTab; onClose: () => void }) {
    return (
        <Space size={6}>
            <span>{tab.title}</span>
            {!tab.affix ? (
                <button
                    aria-label={`关闭页签：${tab.title}`}
                    className="am-shell__tab-close"
                    type="button"
                    onClick={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        onClose();
                    }}
                >
                    ×
                </button>
            ) : null}
        </Space>
    );
}

async function destroyRemovedTabs(
    oldTabs: PageTab[],
    nextTabs: PageTab[],
    destroy: ((cacheKey?: string | string[]) => Promise<void>) | undefined,
) {
    const removed = oldTabs
        .filter((tab) => !nextTabs.some((item) => item.fullPath === tab.fullPath))
        .map((tab) => tab.fullPath);
    if (destroy && removed.length > 0) {
        await destroy(removed);
    }
}

function activeRouteHandle(matches: ReturnType<typeof useMatches>) {
    const matched = [...matches].reverse().find((match) => match.handle);
    return matched?.handle as AppRouteHandle | undefined;
}
