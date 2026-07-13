<script setup lang="ts">
import {
    cloneVNode,
    computed,
    defineComponent,
    h,
    markRaw,
    shallowReactive,
    watch,
    watchEffect,
} from "vue";
import type { Component } from "vue";
import type { RouteLocationNormalizedLoaded } from "vue-router";

import { usePageTabsStore } from "@/stores/pageTabsStore";
import PageTabInstance from "./PageTabInstance.vue";

const props = defineProps<{
    activeComponent: Component;
    route: RouteLocationNormalizedLoaded;
}>();
const tabsStore = usePageTabsStore();
const activeTab = computed(() =>
    tabsStore.tabs.find((tab) => tab.fullPath === props.route.fullPath),
);
const wrappers = shallowReactive(new Map<number, Component>());
const activeWrapper = computed(() => {
    const tab = activeTab.value;
    return tab ? wrappers.get(tab.instanceId) : undefined;
});
const cacheIncludes = computed(() =>
    tabsStore.tabs.filter((tab) => tab.cache).map((tab) => tab.cacheName),
);

watch(
    () => props.route.fullPath,
    () => tabsStore.ensureRouteTab(props.route),
    { immediate: true },
);

watchEffect(() => {
    const liveInstanceIds = new Set(tabsStore.tabs.map((tab) => tab.instanceId));
    for (const instanceId of wrappers.keys()) {
        if (!liveInstanceIds.has(instanceId)) wrappers.delete(instanceId);
    }
    const tab = activeTab.value;
    if (tab && !wrappers.has(tab.instanceId)) {
        wrappers.set(tab.instanceId, createTabWrapper(tab.cacheName));
    }
});

function createTabWrapper(name: string) {
    return markRaw(
        defineComponent({
            name,
            inheritAttrs: false,
            props: {
                fullPath: { type: String, required: true },
                instanceId: { type: Number, required: true },
                version: { type: Number, required: true },
            },
            setup(wrapperProps, { slots }) {
                return () => {
                    const page = slots.default?.()[0];
                    const keyedPage = page ? cloneVNode(page, { key: wrapperProps.version }) : null;
                    return h(
                        PageTabInstance,
                        {
                            fullPath: wrapperProps.fullPath,
                            instanceId: wrapperProps.instanceId,
                            version: wrapperProps.version,
                        },
                        { default: () => keyedPage },
                    );
                };
            },
        }),
    );
}
</script>

<template>
    <KeepAlive :include="cacheIncludes">
        <component
            :is="activeWrapper"
            v-if="activeTab && activeWrapper"
            :key="activeTab.instanceId"
            :full-path="activeTab.fullPath"
            :instance-id="activeTab.instanceId"
            :version="activeTab.version"
        >
            <component :is="activeComponent" />
        </component>
    </KeepAlive>
</template>
