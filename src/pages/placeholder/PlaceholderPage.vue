<script setup lang="ts">
defineOptions({ name: "PlaceholderPage" });

defineProps<{
  title: string;
  description: string;
}>();
</script>

<template>
  <section class="placeholder-page">
    <h1>{{ title }}</h1>
    <p>{{ description }}</p>
  </section>
</template>

<style scoped lang="scss">
.placeholder-page {
  height: 100%;
  overflow: auto;
  padding: 20px;

  h1 {
    margin: 0;
    font-size: 24px;
  }

  p {
    max-width: 680px;
    margin: 8px 0 0;
    color: var(--am-text-muted);
  }
}
</style>
