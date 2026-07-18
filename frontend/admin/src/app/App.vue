<script setup lang="ts">
import { onBeforeUnmount, onMounted } from "vue";
import { useRouter } from "vue-router";
import zhCn from "element-plus/es/locale/lang/zh-cn";

import { UNAUTHENTICATED_EVENT } from "@archive-management/frontend-core/api";

import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";
import { usePageTabsStore } from "@/stores/pageTabsStore";

const router = useRouter();
const sessionStore = useSessionStore();
const permissionStore = usePermissionStore();
const pageTabsStore = usePageTabsStore();

function handleUnauthenticated() {
    sessionStore.clearSession();
    permissionStore.reset();
    pageTabsStore.reset();
    if (router.currentRoute.value.path !== "/login") {
        void router.replace({
            path: "/login",
            query: { redirect: router.currentRoute.value.fullPath },
        });
    }
}

onMounted(() => window.addEventListener(UNAUTHENTICATED_EVENT, handleUnauthenticated));
onBeforeUnmount(() => window.removeEventListener(UNAUTHENTICATED_EVENT, handleUnauthenticated));
</script>

<template>
    <ElConfigProvider :locale="zhCn">
        <RouterView />
    </ElConfigProvider>
</template>
