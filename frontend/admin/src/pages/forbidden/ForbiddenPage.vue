<script setup lang="ts">
import { useRouter } from "vue-router";

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });
const router = useRouter();
</script>

<template>
    <component
        :is="embedded ? 'section' : 'main'"
        class="am-page"
        aria-labelledby="forbidden-title"
    >
        <ElResult
            icon="warning"
            sub-title="当前账号没有访问此功能的权限。如需使用，请联系系统管理员。"
        >
            <template #title>
                <h1 id="forbidden-title">没有访问权限</h1>
            </template>
            <template #extra>
                <ElButton type="primary" @click="router.replace('/')">返回工作台</ElButton>
            </template>
        </ElResult>
    </component>
</template>
