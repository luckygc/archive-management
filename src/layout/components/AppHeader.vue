<script setup lang="ts">
import { ArrowDown, RefreshRight, SwitchButton } from "@element-plus/icons-vue";
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { usePageTabsStore } from "../../app/stores/pageTabs";
import { useSessionStore } from "../../app/stores/session";

const route = useRoute();
const router = useRouter();
const pageTabsStore = usePageTabsStore();
const sessionStore = useSessionStore();

const breadcrumbItems = computed(() =>
  route.matched
    .map((item) => (typeof item.meta.title === "string" ? item.meta.title : ""))
    .filter(Boolean),
);

async function handleLogout() {
  await sessionStore.logoutCurrentUser();
  await router.push({ name: "Login" });
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__nav">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="item in breadcrumbItems" :key="item">
          {{ item }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="app-header__right">
      <el-button
        :icon="RefreshRight"
        circle
        aria-label="刷新当前页"
        text
        @click="pageTabsStore.refreshCurrent()"
      />
      <el-dropdown trigger="click" @command="handleLogout">
        <button class="app-header__user" type="button">
          <span>{{ sessionStore.currentUser?.displayName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped lang="scss">
.app-header {
  display: flex;
  align-items: center;
  height: 56px;
  gap: 16px;
  padding: 0 18px;
  border-bottom: 1px solid var(--am-border);
  background: var(--am-bg-surface);
}

.app-header__nav {
  min-width: 0;
}

.app-header__right {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.app-header__user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  border: none;
  border-radius: 6px;
  padding: 0 10px;
  color: var(--am-text);
  background: transparent;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    background: var(--am-bg-page);
  }
}
</style>
