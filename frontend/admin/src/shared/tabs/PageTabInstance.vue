<script setup lang="ts">
import { provide, toRef } from "vue";

import { pageTabContextKey } from "./pageTabContext";

const props = defineProps<{ fullPath: string; instanceId: number; version: number }>();
provide(pageTabContextKey, {
    fullPath: toRef(props, "fullPath"),
    instanceId: toRef(props, "instanceId"),
    version: toRef(props, "version"),
});
</script>

<template>
    <slot />
</template>
