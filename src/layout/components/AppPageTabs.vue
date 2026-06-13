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
    <div class="app-page-tabs__strip">
      <div
        v-for="tab in pageTabsStore.tabs"
        :key="tab.fullPath"
        class="app-page-tabs__item"
        :class="{ 'is-active': tab.fullPath === route.fullPath }"
      >
        <button class="app-page-tabs__label" type="button" @click="activateTab(tab.fullPath)">
          <span>{{ tab.title }}</span>
        </button>
        <button
          v-if="!tab.affix"
          class="app-page-tabs__close"
          :aria-label="`关闭${tab.title}`"
          type="button"
          @click="closeTab(tab.fullPath)"
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
  height: 100%;
  gap: 4px;
}

.app-page-tabs__strip {
  display: flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
  gap: 4px;
  overflow: hidden;
}

.app-page-tabs__item {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 146px;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--am-text-muted);
  background: transparent;

  &:hover,
  &:focus-within {
    color: var(--am-text);
    background: var(--am-bg-page);
  }

  &.is-active {
    border-color: var(--am-border-strong);
    color: var(--am-text);
    background: var(--am-bg-subtle);
  }
}

.app-page-tabs__label {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  height: 100%;
  border: none;
  padding: 0 8px 0 10px;
  color: inherit;
  background: transparent;
  cursor: pointer;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.app-page-tabs__close {
  flex: none;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 6px;
  border: none;
  border-radius: 4px;
  padding: 0;
  color: inherit;
  background: transparent;
  font-size: 12px;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    background: #e2e8f0;
  }
}

.app-page-tabs__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
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
}
</style>
