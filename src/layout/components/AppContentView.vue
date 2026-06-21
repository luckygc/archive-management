<script setup lang="ts">
import type { Component } from "vue";
import type { VNode } from "vue";
import { cloneVNode, computed, defineComponent, markRaw, watch } from "vue";
import type { RouteLocationNormalizedLoaded } from "vue-router";
import { usePageTabsStore } from "../../app/stores/pageTabs";

const props = defineProps<{
  component: VNode | null;
  route: RouteLocationNormalizedLoaded;
}>();

const pageTabsStore = usePageTabsStore();
const pageComponentCache = new Map<string, { component: Component; state: { vnode: VNode } }>();

function resolvePageComponent(componentName: string, vnode: VNode) {
  const cached = pageComponentCache.get(componentName);
  if (cached) {
    cached.state.vnode = vnode;
    return cached.component;
  }

  const state = { vnode };
  const component = markRaw(
    defineComponent({
      name: componentName,
      setup() {
        return () => cloneVNode(state.vnode);
      },
    }),
  );
  pageComponentCache.set(componentName, { component, state });
  return component;
}

const activePageComponent = computed(() => {
  const activeTab = pageTabsStore.activeTab;
  if (!activeTab || !props.component) {
    return null;
  }
  return resolvePageComponent(activeTab.pageComponentName, props.component);
});

watch(
  () => props.route,
  () => {
    pageTabsStore.ensureTabEntry(props.route);
  },
  { immediate: true },
);

watch(
  () => pageTabsStore.tabs.map((item) => item.pageComponentName),
  (componentNames) => {
    const existingNames = new Set(componentNames);
    for (const componentName of pageComponentCache.keys()) {
      if (!existingNames.has(componentName)) {
        pageComponentCache.delete(componentName);
      }
    }
  },
);
</script>

<template>
  <keep-alive :include="pageTabsStore.cacheNames">
    <component :is="activePageComponent" :key="pageTabsStore.activeTab?.pageComponentKey" />
  </keep-alive>
</template>
