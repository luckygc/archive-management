<script setup lang="ts">
import { computed } from "vue";
import type { RouteRecordRaw } from "vue-router";

import { canAccessRoute } from "@/app/routes";
import { usePermissionStore } from "@/stores/permissionStore";

defineOptions({ name: "RouteMenuItem" });

const props = withDefaults(
    defineProps<{
        routeRecord: RouteRecordRaw;
        parentPath?: string;
    }>(),
    { parentPath: "" },
);

const path = computed(() => joinPath(props.parentPath, String(props.routeRecord.path)));
const permissionStore = usePermissionStore();
const accessible = computed(() => canAccessRoute(props.routeRecord, permissionStore));
const children = computed(() =>
    (props.routeRecord.children ?? []).filter(
        (item) => item.meta?.menu === true && canAccessRoute(item, permissionStore),
    ),
);
let prefetchPromise: Promise<unknown> | undefined;

function joinPath(parentPath: string, path: string) {
    if (path.startsWith("/")) return path;
    if (path === "") return parentPath || "/";
    return `/${[parentPath, path].filter(Boolean).join("/")}`.replaceAll(/\/{2,}/g, "/");
}

function prefetchRouteComponent() {
    const component = props.routeRecord.component;
    if (prefetchPromise || typeof component !== "function") return;
    try {
        const loadComponent = component as () => unknown;
        prefetchPromise = Promise.resolve(loadComponent()).catch(() => {
            prefetchPromise = undefined;
        });
    } catch {
        prefetchPromise = undefined;
    }
}
</script>

<template>
    <template v-if="accessible">
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
        <ElMenuItem
            v-else
            :index="path"
            @mouseenter="prefetchRouteComponent"
            @focusin="prefetchRouteComponent"
        >
            <ElIcon v-if="routeRecord.meta?.icon">
                <component :is="routeRecord.meta.icon" />
            </ElIcon>
            <span>{{ routeRecord.meta?.title }}</span>
        </ElMenuItem>
    </template>
</template>
