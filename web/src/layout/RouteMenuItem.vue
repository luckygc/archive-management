<script setup lang="ts">
import { computed } from "vue";
import type { RouteRecordRaw } from "vue-router";

defineOptions({ name: "RouteMenuItem" });

const props = withDefaults(
    defineProps<{
        routeRecord: RouteRecordRaw;
        parentPath?: string;
    }>(),
    { parentPath: "" },
);

const path = computed(() => joinPath(props.parentPath, String(props.routeRecord.path)));
const children = computed(() =>
    (props.routeRecord.children ?? []).filter((item) => item.meta?.menu),
);

function joinPath(parentPath: string, path: string) {
    if (path.startsWith("/")) return path;
    if (path === "") return parentPath || "/";
    return `/${[parentPath, path].filter(Boolean).join("/")}`.replaceAll(/\/{2,}/g, "/");
}
</script>

<template>
    <ElSubMenu v-if="children.length" :index="path">
        <template #title>
            <ElIcon v-if="routeRecord.meta?.icon">
                <component :is="routeRecord.meta.icon" />
            </ElIcon>
            <span>{{ routeRecord.meta?.title }}</span>
        </template>
        <RouteMenuItem
            v-for="child in children"
            :key="String(child.name ?? child.path)"
            :route-record="child"
            :parent-path="path"
        />
    </ElSubMenu>
    <ElMenuItem v-else :index="path">
        <ElIcon v-if="routeRecord.meta?.icon">
            <component :is="routeRecord.meta.icon" />
        </ElIcon>
        <span>{{ routeRecord.meta?.title }}</span>
    </ElMenuItem>
</template>
