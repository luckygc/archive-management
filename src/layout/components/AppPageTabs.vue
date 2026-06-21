<script setup lang="ts">
import { Close, MoreFilled } from "@element-plus/icons-vue";
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { usePageTabsStore } from "../../app/stores/pageTabs";

const route = useRoute();
const router = useRouter();
const pageTabsStore = usePageTabsStore();

type TabClickContext = {
  paneName?: string | number;
  props?: {
    name?: string | number;
  };
};

const canCloseCurrent = computed(() => {
  const current = pageTabsStore.tabs.find((item) => item.fullPath === route.fullPath);
  return Boolean(current && !current.affix);
});

function readPaneName(tab: TabClickContext) {
  const name = tab.paneName ?? tab.props?.name;
  return typeof name === "string" ? name : "";
}

async function activateTab(fullPath: string) {
  if (fullPath !== route.fullPath) {
    await router.push(fullPath);
  }
}

async function handleTabClick(tab: TabClickContext) {
  const fullPath = readPaneName(tab);
  if (fullPath) {
    await activateTab(fullPath);
  }
}

async function closeTab(fullPath: string) {
  const isClosingCurrent = fullPath === route.fullPath;
  const nextPath = pageTabsStore.resolveNextPathAfterClose(fullPath);
  if (isClosingCurrent && nextPath !== route.fullPath) {
    await router.push(nextPath);
  }
  pageTabsStore.closeTab(fullPath);
}

async function closeCurrent() {
  if (canCloseCurrent.value) {
    await closeTab(route.fullPath);
  }
}

async function closeOthers() {
  pageTabsStore.closeOthers(route.fullPath);
}

async function closeAll() {
  const nextPath = pageTabsStore.closeAll();
  if (nextPath !== route.fullPath) {
    await router.push(nextPath);
  }
}

async function handleTabCommand(command: string) {
  if (command === "current") {
    await closeCurrent();
    return;
  }
  if (command === "others") {
    await closeOthers();
    return;
  }
  await closeAll();
}
</script>

<template>
  <nav class="app-page-tabs" aria-label="已打开页面">
    <el-tabs
      class="app-page-tabs__tabs"
      type="card"
      :model-value="route.fullPath"
      @tab-click="handleTabClick"
    >
      <el-tab-pane v-for="tab in pageTabsStore.tabs" :key="tab.fullPath" :name="tab.fullPath">
        <template #label>
          <span class="app-page-tabs__label">
            <span class="app-page-tabs__title">{{ tab.title }}</span>
            <button
              v-if="!tab.affix"
              class="app-page-tabs__close"
              :aria-label="`关闭${tab.title}`"
              type="button"
              @click.stop.prevent="closeTab(tab.fullPath)"
            >
              <el-icon><Close /></el-icon>
            </button>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>
    <el-dropdown trigger="click" @command="handleTabCommand">
      <button class="app-page-tabs__more" type="button" aria-label="页签操作">
        <el-icon><MoreFilled /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="current" :disabled="!canCloseCurrent"
            >关闭当前</el-dropdown-item
          >
          <el-dropdown-item command="others">关闭其他</el-dropdown-item>
          <el-dropdown-item command="all">关闭全部</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </nav>
</template>

<style scoped lang="scss">
.app-page-tabs {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 40px;
  gap: 8px;
  padding: 0 14px;
  border-bottom: 1px solid var(--am-border);
  background: var(--am-bg-subtle);
}

.app-page-tabs__tabs {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.app-page-tabs__label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  max-width: 176px;
}

.app-page-tabs__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-page-tabs__close {
  flex: none;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  padding: 0;
  color: inherit;
  background: transparent;
  font-size: 12px;
  opacity: 0.72;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: var(--am-text);
    background: var(--am-border);
  }
}

.app-page-tabs__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--am-border);
  border-radius: 6px;
  color: var(--am-text-muted);
  background: var(--am-bg-surface);
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: var(--am-text);
    border-color: var(--am-border-strong);
    background: var(--am-bg-page);
  }
}
</style>
