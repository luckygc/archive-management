<script setup lang="ts">
defineOptions({ name: "ArchiveLibraryPage" });

const rows = [
  { code: "KJ-PZ-2026", name: "会计凭证", category: "会计档案", retention: "30 年", count: 82460 },
  { code: "HT-2026", name: "合同档案", category: "文书档案", retention: "长期", count: 18620 },
  { code: "YX-DZ-2026", name: "影像电子件", category: "电子档案", retention: "永久", count: 27380 },
];
</script>

<template>
  <section class="archive-library-page">
    <header class="archive-library-page__toolbar">
      <el-button type="primary">新增档案</el-button>
    </header>
    <section class="archive-library-page__body">
      <el-table :data="rows" height="100%">
        <el-table-column prop="code" label="档案表编码" width="160" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="140" />
        <el-table-column prop="retention" label="保管期限" width="120" />
        <el-table-column prop="count" label="记录数" width="120" />
      </el-table>
    </section>
  </section>
</template>

<style scoped lang="scss">
.archive-library-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 20px;
}

.archive-library-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-bottom: 16px;
}

.archive-library-page__body {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--am-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--am-bg-surface);
}
</style>
