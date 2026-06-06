<script setup lang="ts">
import { ArrowDown, SwitchButton } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { useSessionStore } from "../../app/stores/session";

const router = useRouter();
const sessionStore = useSessionStore();

async function handleLogout() {
  await sessionStore.logoutCurrentUser();
  await router.push({ name: "Login" });
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__title">
      <strong>档案管理</strong>
      <span>PC 管理端</span>
    </div>
    <div class="app-header__right">
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
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  border-bottom: 1px solid var(--am-border);
  background: var(--am-bg-surface);
}

.app-header__title {
  display: flex;
  align-items: baseline;
  gap: 10px;

  strong {
    font-size: 16px;
  }

  span {
    color: var(--am-text-muted);
    font-size: 13px;
  }
}

.app-header__right {
  display: flex;
  align-items: center;
}

.app-header__user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  border: none;
  padding: 0 8px;
  color: var(--am-text);
  background: transparent;
  cursor: pointer;
}
</style>
