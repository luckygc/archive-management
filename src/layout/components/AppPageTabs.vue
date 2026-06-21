<script setup lang="ts">
import { Close, MoreFilled } from "@element-plus/icons-vue";
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { usePageTabsStore } from "../../app/stores/pageTabs";

const route = useRoute();
const router = useRouter();
const pageTabsStore = usePageTabsStore();

const canCloseCurrent = computed(() => {
  const current = pageTabsStore.tabs.find((item) => item.fullPath === route.fullPath);
  return Boolean(current && !current.affix);
});

const currentTabIndex = computed(() =>
  pageTabsStore.tabs.findIndex((item) => item.fullPath === route.fullPath),
);

const canCloseLeft = computed(() => {
  const currentIndex = currentTabIndex.value;
  return (
    currentIndex > 0 &&
    pageTabsStore.tabs.some((item, index) => index < currentIndex && !item.affix)
  );
});

const canCloseRight = computed(() => {
  const currentIndex = currentTabIndex.value;
  return (
    currentIndex >= 0 &&
    pageTabsStore.tabs.some((item, index) => index > currentIndex && !item.affix)
  );
});

const canCloseOthers = computed(() =>
  pageTabsStore.tabs.some((item) => item.fullPath !== route.fullPath && !item.affix),
);

function isActiveTab(fullPath: string) {
  return fullPath === route.fullPath;
}

async function activateTab(fullPath: string) {
  if (fullPath !== route.fullPath) {
    await router.push(fullPath);
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

async function closeLeft() {
  if (canCloseLeft.value) {
    pageTabsStore.closeLeft(route.fullPath);
  }
}

async function closeRight() {
  if (canCloseRight.value) {
    pageTabsStore.closeRight(route.fullPath);
  }
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
  if (command === "left") {
    await closeLeft();
    return;
  }
  if (command === "right") {
    await closeRight();
    return;
  }
  await closeAll();
}
</script>

<template>
  <nav class="app-page-tabs" aria-label="已打开页面">
    <div class="app-page-tabs__tabs" role="list">
      <div
        v-for="tab in pageTabsStore.tabs"
        :key="tab.fullPath"
        class="app-page-tabs__item"
        :class="{ 'is-active': isActiveTab(tab.fullPath) }"
        role="listitem"
      >
        <button
          class="app-page-tabs__tab"
          type="button"
          :aria-current="isActiveTab(tab.fullPath) ? 'page' : undefined"
          @click="activateTab(tab.fullPath)"
        >
          <span class="app-page-tabs__title">{{ tab.title }}</span>
        </button>
        <button
          v-if="!tab.affix"
          class="app-page-tabs__close"
          :aria-label="`关闭${tab.title}`"
          type="button"
          @click.stop.prevent="closeTab(tab.fullPath)"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </div>
    <el-dropdown trigger="click" @command="handleTabCommand">
      <button class="app-page-tabs__more" type="button" aria-label="页签操作">
        <el-icon><MoreFilled /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="current" :disabled="!canCloseCurrent"
            >关闭当前</el-dropdown-item
          >
          <el-dropdown-item command="others" :disabled="!canCloseOthers">关闭其他</el-dropdown-item>
          <el-dropdown-item command="left" :disabled="!canCloseLeft">关闭左侧</el-dropdown-item>
          <el-dropdown-item command="right" :disabled="!canCloseRight">关闭右侧</el-dropdown-item>
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
  font-size: 14px;
}

.app-page-tabs__tabs {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 6px;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.app-page-tabs__item {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  min-width: 104px;
  max-width: 204px;
  height: 32px;
  border: 1px solid var(--am-border);
  border-radius: 6px;
  color: var(--am-text-muted);
  background: var(--am-bg-page);

  &:hover {
    color: var(--am-text);
    border-color: var(--am-border-strong);
  }

  &.is-active {
    color: var(--am-text);
    border-color: var(--am-border-strong);
    background: var(--am-bg-surface);
  }

  &.is-active .app-page-tabs__close {
    opacity: 0.72;
  }
}

.app-page-tabs__tab {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  padding: 0 10px 0 12px;
  color: inherit;
  background: transparent;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid var(--am-primary);
    outline-offset: -2px;
  }
}

.app-page-tabs__title {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 30px;
}

.app-page-tabs__close {
  flex: none;
  margin-right: 8px;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  padding: 0;
  color: inherit;
  background: transparent;
  font-size: 13px;
  opacity: 0.54;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: var(--am-text);
    background: var(--am-border);
    opacity: 0.86;
  }

  &:focus-visible {
    outline: 2px solid var(--am-primary);
    outline-offset: 1px;
  }
}

.app-page-tabs__more {
  align-self: center;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 32px;
  border: none;
  border-radius: 6px;
  color: var(--am-text-muted);
  background: transparent;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: var(--am-text);
    background: var(--am-bg-page);
  }

  &:focus-visible {
    outline: 2px solid var(--am-primary);
    outline-offset: 1px;
  }
}
</style>
