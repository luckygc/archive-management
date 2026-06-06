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
      <strong>档案管理</strong>
    </div>
    <el-menu class="app-sidebar__menu" router :default-active="$route.path">
      <el-menu-item v-for="item in menus" :key="item.id" :index="item.path ?? item.id">
        <el-icon v-if="item.icon">
          <component :is="resolveIcon(item.icon)" />
        </el-icon>
        <span>{{ item.title }}</span>
      </el-menu-item>
    </el-menu>
  </aside>
</template>

<style scoped lang="scss">
.app-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid #172033;
  background: #111827;
}

.app-sidebar__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 18px;
  color: #fff;

  span {
    display: grid;
    width: 32px;
    height: 32px;
    place-items: center;
    border-radius: 6px;
    color: #1d4ed8;
    background: #fff;
    font-size: 12px;
    font-weight: 700;
  }

  strong {
    font-size: 16px;
  }
}

.app-sidebar__menu {
  flex: 1;
  min-height: 0;
  border-right: none;
  background: transparent;
}
</style>
