<script setup lang="ts">
import { useRouter } from "vue-router";

import { useSessionStore } from "@/stores/sessionStore";

const props = withDefaults(defineProps<{ redirect?: string }>(), { redirect: "/" });
const router = useRouter();
const sessionStore = useSessionStore();

async function retry() {
    sessionStore.reset();
    await router.replace(props.redirect);
}
</script>

<template>
    <main class="am-authentication-loading">
        <ElResult
            icon="error"
            title="会话校验失败"
            :sub-title="sessionStore.initializationError || '暂时无法连接认证服务'"
        >
            <template #extra><ElButton type="primary" @click="retry">重新校验</ElButton></template>
        </ElResult>
    </main>
</template>
