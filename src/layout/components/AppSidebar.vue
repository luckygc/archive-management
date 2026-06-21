<script setup lang="ts">
import * as ElementPlusIcons from "@element-plus/icons-vue";
import { Expand, Fold } from "@element-plus/icons-vue";
import { computed } from "vue";
import { routes } from "../../app/router/routes";
import type { MenuNode } from "../../shared/types/menu";
import AppSidebarMenuItem from "./AppSidebarMenuItem.vue";

defineProps<{
  collapsed: boolean;
}>();

defineEmits<{
  toggleCollapse: [];
}>();

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

function collectMenuGroupIds(items: MenuNode[]) {
  return items.flatMap((item) => [
    ...(item.children?.length ? [item.path ?? item.id] : []),
    ...collectMenuGroupIds(item.children ?? []),
  ]);
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
const defaultOpeneds = computed(() => collectMenuGroupIds(menus.value));
</script>

<template>
  <aside class="app-sidebar" :class="{ 'is-collapsed': collapsed }">
    <div class="app-sidebar__brand">
      <span class="app-sidebar__mark">AM</span>
      <div v-show="!collapsed">
        <strong>档案管理</strong>
        <small>Archive Management</small>
      </div>
    </div>
    <el-menu
      class="app-sidebar__menu"
      router
      :collapse="collapsed"
      :default-active="$route.path"
      :default-openeds="defaultOpeneds"
      :collapse-transition="false"
    >
      <AppSidebarMenuItem
        v-for="item in menus"
        :key="item.id"
        :item="item"
        :resolve-icon="resolveIcon"
      />
    </el-menu>
    <div class="app-sidebar__footer">
      <el-button
        class="app-sidebar__collapse"
        :icon="collapsed ? Expand : Fold"
        circle
        :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'"
        @click="$emit('toggleCollapse')"
      />
    </div>
  </aside>
</template>

<style scoped lang="scss">
.app-sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--am-border);
  background: var(--am-bg-surface);
  overflow: hidden;
}

.app-sidebar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid var(--am-border);

  > div {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  strong,
  small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--am-text);
    font-size: 15px;
    line-height: 1.2;
  }

  small {
    color: var(--am-text-muted);
    font-size: 11px;
    line-height: 1.2;
  }
}

.app-sidebar__mark {
  display: grid;
  flex: none;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 8px;
  color: #fff;
  background: var(--el-color-primary);
  font-size: 13px;
  font-weight: 700;
}

.app-sidebar.is-collapsed .app-sidebar__brand {
  justify-content: center;
  padding: 0 10px;
}

.app-sidebar__menu {
  --el-menu-item-height: 42px;
  --el-menu-sub-item-height: 42px;

  flex: 1;
  min-height: 0;
  border-right: none;
  overflow: auto;
}

.app-sidebar__footer {
  display: flex;
  flex: none;
  justify-content: flex-end;
  padding: 10px 14px 14px;
}

.app-sidebar.is-collapsed .app-sidebar__footer {
  justify-content: center;
  padding-inline: 0;
}
</style>
