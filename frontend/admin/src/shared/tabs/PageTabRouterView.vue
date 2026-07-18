<script setup lang="ts">
import PageTabCache from "./PageTabCache.vue";
</script>

<template>
    <RouterView v-slot="{ Component, route }">
        <PageTabCache v-if="Component" :route="route" :active-component="Component" />
    </RouterView>
</template>
