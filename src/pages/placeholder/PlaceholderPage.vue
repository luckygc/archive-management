<script setup lang="ts">
defineOptions({ name: "PlaceholderPage" });
</script>

<template>
  <section class="placeholder-page">
    <el-empty description="功能建设中" />
  </section>
</template>

<style scoped lang="scss">
.placeholder-page {
  display: grid;
  height: 100%;
  overflow: auto;
  padding: 20px;
  place-items: center;
}
</style>
