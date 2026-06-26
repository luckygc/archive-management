<script setup lang="ts">
import { ref } from "vue";
import AppHeader from "./components/AppHeader.vue";
import AppContentView from "./components/AppContentView.vue";
import AppPageTabs from "./components/AppPageTabs.vue";
import AppSidebar from "./components/AppSidebar.vue";

const sidebarCollapsed = ref(false);
</script>

<template>
    <div class="app-layout" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
        <AppSidebar
            class="app-layout__sidebar"
            :collapsed="sidebarCollapsed"
            @toggle-collapse="sidebarCollapsed = !sidebarCollapsed"
        />
        <div class="app-layout__main">
            <AppHeader class="app-layout__header" />
            <AppPageTabs class="app-layout__tabs" />
            <main class="app-layout__content">
                <router-view v-slot="{ Component, route }">
                    <AppContentView :component="Component" :route="route" />
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
    transition: grid-template-columns 180ms ease-out;
}

.app-layout.is-sidebar-collapsed {
    grid-template-columns: 72px minmax(0, 1fr);
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

.app-layout__tabs {
    flex-shrink: 0;
}

.app-layout__content {
    flex: 1;
    min-width: 0;
    min-height: 0;
    overflow: hidden;
}
</style>
