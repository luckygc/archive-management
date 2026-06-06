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
        path: "archive-library",
        name: "ArchiveLibrary",
        component: () => import("../../pages/archive-library/ArchiveLibraryPage.vue"),
        meta: {
          title: "档案库",
          icon: "FolderOpened",
          isMenu: true,
          keepAlive: true,
          cacheName: "ArchiveLibraryPage",
        },
      },
      {
        path: "transfer",
        name: "ArchiveTransfer",
        component: () => import("../../pages/placeholder/PlaceholderPage.vue"),
        props: {
          title: "移交接收",
          description: "承载档案移交、接收、质检和入库流程。",
        },
        meta: {
          title: "移交接收",
          icon: "Switch",
          isMenu: true,
        },
      },
      {
        path: "borrow",
        name: "ArchiveBorrow",
        component: () => import("../../pages/placeholder/PlaceholderPage.vue"),
        props: {
          title: "借阅利用",
          description: "承载借阅申请、审批、归还和利用记录。",
        },
        meta: {
          title: "借阅利用",
          icon: "Reading",
          isMenu: true,
        },
      },
      {
        path: "system",
        name: "SystemManagement",
        component: () => import("../../pages/placeholder/PlaceholderPage.vue"),
        props: {
          title: "系统配置",
          description: "维护用户、角色、存储、字典和系统参数。",
        },
        meta: {
          title: "系统配置",
          icon: "Setting",
          isMenu: true,
        },
      },
    ],
  },
];
