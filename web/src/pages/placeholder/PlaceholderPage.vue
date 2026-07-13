<script setup lang="ts">
defineProps<{ title: string }>();
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>{{ title }}</h1>
        </div>
        <el-card shadow="never"><el-text type="info">该功能入口已接入 Vue 主壳。</el-text></el-card>
    </section>
</template>
