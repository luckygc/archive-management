import type { RouteRecordRaw } from "vue-router";

export const routes: RouteRecordRaw[] = [
    {
        path: "/login",
        name: "Login",
        component: () => import("../../pages/login/LoginPage.vue"),
        meta: {
            title: "登录",
        },
    },
    {
        path: "/",
        name: "Root",
        component: () => import("../../layout/AppLayout.vue"),
        children: [
            {
                path: "",
                name: "Dashboard",
                component: () => import("../../pages/dashboard/DashboardPage.vue"),
                meta: {
                    title: "工作台",
                    icon: "DataBoard",
                    isMenu: true,
                    keepAlive: true,
                    cacheName: "DashboardPage",
                    affixTab: true,
                },
            },
            {
                path: "archive",
                name: "ArchiveManagement",
                meta: {
                    title: "档案管理",
                    icon: "FolderOpened",
                    isMenu: true,
                },
                children: [
                    {
                        path: "library",
                        name: "ArchiveLibrary",
                        component: () =>
                            import("../../pages/archive-library/ArchiveLibraryPage.vue"),
                        meta: {
                            title: "档案库",
                            icon: "Collection",
                            isMenu: true,
                            keepAlive: true,
                            cacheName: "ArchiveLibraryPage",
                        },
                    },
                    {
                        path: "library/:id/edit",
                        name: "ArchiveRecordEdit",
                        component: () =>
                            import("../../pages/archive-library/ArchiveRecordEditPage.vue"),
                        meta: {
                            title: "编辑档案",
                            keepAlive: true,
                            cacheName: "ArchiveRecordEditPage",
                        },
                    },
                    {
                        path: "library/:id",
                        name: "ArchiveRecordDetail",
                        component: () =>
                            import("../../pages/archive-library/ArchiveRecordDetailPage.vue"),
                        meta: {
                            title: "档案详情",
                            keepAlive: true,
                            cacheName: "ArchiveRecordDetailPage",
                        },
                    },
                    {
                        path: "catalog",
                        name: "ArchiveCatalog",
                        meta: {
                            title: "目录配置",
                            icon: "Files",
                            isMenu: true,
                        },
                        children: [
                            {
                                path: "fonds",
                                name: "ArchiveFonds",
                                component: () =>
                                    import("../../pages/archive-fonds/ArchiveFondsPage.vue"),
                                meta: {
                                    title: "全宗管理",
                                    isMenu: true,
                                    keepAlive: true,
                                    cacheName: "ArchiveFondsPage",
                                },
                            },
                            {
                                path: "categories",
                                name: "ArchiveCategories",
                                component: () =>
                                    import("../../pages/archive-categories/ArchiveCategoriesPage.vue"),
                                meta: {
                                    title: "档案分类",
                                    isMenu: true,
                                    keepAlive: true,
                                    cacheName: "ArchiveCategoriesPage",
                                },
                            },
                        ],
                    },
                ],
            },
            {
                path: "table-demo",
                name: "TanStackTableDemo",
                component: () => import("../../pages/table-demo/TanStackTableDemoPage.vue"),
                meta: {
                    title: "表格 Demo",
                    icon: "Grid",
                    isMenu: true,
                    keepAlive: true,
                    cacheName: "TanStackTableDemoPage",
                },
            },
            {
                path: "transfer",
                name: "ArchiveTransfer",
                meta: {
                    title: "移交接收",
                    icon: "Switch",
                    isMenu: true,
                },
                children: [
                    {
                        path: "batches",
                        name: "TransferBatches",
                        component: () => import("../../pages/placeholder/PlaceholderPage.vue"),
                        meta: {
                            title: "移交批次",
                            isMenu: true,
                        },
                    },
                    {
                        path: "receiving",
                        name: "TransferReceiving",
                        component: () => import("../../pages/placeholder/PlaceholderPage.vue"),
                        meta: {
                            title: "接收登记",
                            isMenu: true,
                        },
                    },
                ],
            },
            {
                path: "borrow",
                name: "ArchiveBorrow",
                meta: {
                    title: "借阅利用",
                    icon: "Reading",
                    isMenu: true,
                },
                children: [
                    {
                        path: "requests",
                        name: "BorrowRequests",
                        component: () => import("../../pages/placeholder/PlaceholderPage.vue"),
                        meta: {
                            title: "借阅申请",
                            isMenu: true,
                        },
                    },
                    {
                        path: "records",
                        name: "BorrowRecords",
                        component: () => import("../../pages/placeholder/PlaceholderPage.vue"),
                        meta: {
                            title: "利用记录",
                            isMenu: true,
                        },
                    },
                ],
            },
            {
                path: "system",
                name: "SystemManagement",
                meta: {
                    title: "系统配置",
                    icon: "Setting",
                    isMenu: true,
                },
                children: [
                    {
                        path: "users",
                        name: "SystemUsers",
                        component: () => import("../../pages/placeholder/PlaceholderPage.vue"),
                        meta: {
                            title: "用户管理",
                            icon: "User",
                            isMenu: true,
                        },
                    },
                    {
                        path: "permissions",
                        name: "SystemPermissions",
                        meta: {
                            title: "权限配置",
                            icon: "Lock",
                            isMenu: true,
                        },
                        children: [
                            {
                                path: "roles",
                                name: "SystemRoles",
                                component: () =>
                                    import("../../pages/placeholder/PlaceholderPage.vue"),
                                meta: {
                                    title: "角色管理",
                                    isMenu: true,
                                },
                            },
                            {
                                path: "positions",
                                name: "SystemPositions",
                                component: () =>
                                    import("../../pages/placeholder/PlaceholderPage.vue"),
                                meta: {
                                    title: "岗位管理",
                                    isMenu: true,
                                },
                            },
                        ],
                    },
                    {
                        path: "storage",
                        name: "SystemStorage",
                        component: () => import("../../pages/placeholder/PlaceholderPage.vue"),
                        meta: {
                            title: "存储配置",
                            icon: "Coin",
                            isMenu: true,
                        },
                    },
                ],
            },
        ],
    },
];
