<script setup lang="ts">
import type { VNode } from "vue";
import { watch } from "vue";
import type { RouteLocationNormalizedLoaded } from "vue-router";
import { usePageTabsStore } from "../../app/stores/pageTabs";

const props = defineProps<{
  component: VNode | null;
  route: RouteLocationNormalizedLoaded;
}>();

const pageTabsStore = usePageTabsStore();

watch(
  [() => props.route, () => props.component],
  () => {
    pageTabsStore.ensureTabEntry(props.route, props.component);
  },
  { immediate: true },
);
</script>

<template>
  <keep-alive :include="pageTabsStore.cacheNames">
    <component
      :is="pageTabsStore.activeTab?.pageComponent"
      :key="pageTabsStore.activeTab?.pageComponentKey"
    />
  </keep-alive>
</template>
