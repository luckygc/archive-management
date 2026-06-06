<script setup lang="ts">
import { usePageTabsStore } from "../app/stores/pageTabs";
import AppHeader from "./components/AppHeader.vue";
import AppSidebar from "./components/AppSidebar.vue";

const pageTabsStore = usePageTabsStore();
</script>

<template>
  <div class="app-layout">
    <AppSidebar class="app-layout__sidebar" />
    <div class="app-layout__main">
      <AppHeader class="app-layout__header" />
      <main class="app-layout__content">
        <router-view v-slot="{ Component, route }">
          <keep-alive :include="pageTabsStore.cacheNames">
            <component
              :is="Component"
              v-if="route.meta.keepAlive"
              :key="`${route.fullPath}:${pageTabsStore.refreshKey}`"
            />
          </keep-alive>
          <component
            :is="Component"
            v-if="!route.meta.keepAlive"
            :key="`${route.fullPath}:${pageTabsStore.refreshKey}`"
          />
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-layout {
  display: grid;
  width: 100%;
  height: 100%;
  min-width: 1024px;
  min-height: 0;
  overflow: hidden;
  grid-template-columns: 236px minmax(0, 1fr);
  background: var(--am-bg-page);
}

.app-layout__sidebar,
.app-layout__main {
  min-width: 0;
  min-height: 0;
}

.app-layout__main {
  display: flex;
  flex-direction: column;
}

.app-layout__header {
  flex-shrink: 0;
}

.app-layout__content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
</style>
