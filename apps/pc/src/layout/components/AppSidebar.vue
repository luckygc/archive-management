<script setup lang="ts">
import * as ElementPlusIcons from "@element-plus/icons-vue";
import { computed } from "vue";
import { routes } from "../../app/router/routes";
import type { MenuNode } from "../../shared/types/menu";

function joinPath(parentPath: string, path: string) {
  if (path.startsWith("/")) {
    return path;
  }
  const base = parentPath.endsWith("/") ? parentPath.slice(0, -1) : parentPath;
  return `${base}/${path}`.replace(/\/+/g, "/");
}

function mapRouteToMenu(route: (typeof routes)[number], parentPath = ""): MenuNode | null {
  const fullPath = joinPath(parentPath, route.path);
  const children = (route.children ?? [])
    .map((item) => mapRouteToMenu(item, fullPath))
    .filter((item): item is MenuNode => Boolean(item));
  const title = typeof route.meta?.title === "string" ? route.meta.title : "";

  if (route.meta?.isMenu !== true || !title) {
    if (children.length === 1) {
      return children[0];
    }
    return children.length > 0 ? { id: fullPath, title: "", children } : null;
  }

  return {
    id: String(route.name ?? fullPath),
    title,
    path: fullPath,
    icon: typeof route.meta.icon === "string" ? route.meta.icon : undefined,
    children: children.length > 0 ? children : undefined,
  };
}

function resolveIcon(name?: string) {
  if (!name) {
    return undefined;
  }
  return ElementPlusIcons[name as keyof typeof ElementPlusIcons];
}

const menus = computed(() =>
  routes
    .map((route) => mapRouteToMenu(route))
    .flatMap((item) => {
      if (!item) {
        return [];
      }
      if (!item.title && item.children) {
        return item.children;
      }
      return [item];
    }),
);
</script>

<template>
  <aside class="app-sidebar">
    <div class="app-sidebar__brand">
      <span>AM</span>
      <div>
        <strong>档案管理</strong>
        <small>Archive Management</small>
      </div>
    </div>
    <div class="app-sidebar__section">业务导航</div>
    <el-menu class="app-sidebar__menu" router :default-active="$route.path">
      <el-menu-item v-for="item in menus" :key="item.id" :index="item.path ?? item.id">
        <el-icon v-if="item.icon">
          <component :is="resolveIcon(item.icon)" />
        </el-icon>
        <span>{{ item.title }}</span>
      </el-menu-item>
    </el-menu>
    <div class="app-sidebar__meta">
      <span>运行环境</span>
      <strong>本地开发</strong>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.app-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--am-sidebar-border);
  background: var(--am-sidebar-bg);
}

.app-sidebar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 18px;
  color: #fff;

  span {
    display: grid;
    width: 32px;
    height: 32px;
    place-items: center;
    border-radius: 6px;
    color: var(--am-primary);
    background: #fff;
    font-size: 12px;
    font-weight: 700;
  }

  div {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  strong {
    font-size: 16px;
    line-height: 1.2;
  }

  small {
    color: var(--am-sidebar-text-muted);
    font-size: 12px;
    line-height: 1.2;
  }
}

.app-sidebar__section {
  padding: 12px 18px 8px;
  color: var(--am-sidebar-text-muted);
  font-size: 12px;
}

.app-sidebar__menu {
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: #1f2937;
  --el-menu-active-color: #fff;
  --el-menu-text-color: var(--am-sidebar-text);
  --el-menu-item-height: 42px;
  --el-menu-sub-item-height: 42px;

  flex: 1;
  min-height: 0;
  border-right: none;
  padding: 0 10px 12px;
  overflow: auto;
}

.app-sidebar__menu :deep(.el-menu-item) {
  height: 42px;
  margin: 3px 0;
  border-radius: 6px;
  padding-inline: 12px;
}

.app-sidebar__menu :deep(.el-menu-item.is-active) {
  background: var(--am-sidebar-bg-active);
}

.app-sidebar__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 44px;
  margin: 0 12px 12px;
  border-top: 1px solid var(--am-sidebar-border);
  padding: 12px 6px 0;
  color: var(--am-sidebar-text-muted);
  font-size: 12px;

  strong {
    color: var(--am-sidebar-text);
    font-size: 12px;
    font-weight: 500;
  }
}
</style>
