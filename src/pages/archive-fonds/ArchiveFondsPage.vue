<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import {
  createArchiveFonds,
  deleteArchiveFonds,
  listArchiveFonds,
  updateArchiveFonds,
} from "../../shared/api/archive";
import type { ArchiveFondsCommand, ArchiveFondsDto } from "../../shared/types/archive";

defineOptions({ name: "ArchiveFondsPage" });

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingId = ref<string>();
const rows = ref<ArchiveFondsDto[]>([]);
const form = reactive<ArchiveFondsCommand>({
  fondsCode: "",
  fondsName: "",
  enabled: true,
  sortOrder: 0,
});

function resetForm(row?: ArchiveFondsDto) {
  editingId.value = row?.id;
  form.fondsCode = row?.fondsCode ?? "";
  form.fondsName = row?.fondsName ?? "";
  form.enabled = row?.enabled ?? true;
  form.sortOrder = row?.sortOrder ?? 0;
}

async function loadRows() {
  loading.value = true;
  try {
    rows.value = await listArchiveFonds();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  resetForm();
  dialogVisible.value = true;
}

function openEdit(row: ArchiveFondsDto) {
  resetForm(row);
  dialogVisible.value = true;
}

async function submit() {
  saving.value = true;
  try {
    if (editingId.value) {
      await updateArchiveFonds(editingId.value, form);
    } else {
      await createArchiveFonds(form);
    }
    ElMessage.success("已保存");
    dialogVisible.value = false;
    await loadRows();
  } finally {
    saving.value = false;
  }
}

async function remove(row: ArchiveFondsDto) {
  await ElMessageBox.confirm(`确定删除全宗“${row.fondsName}”？`, "删除全宗", {
    type: "warning",
  });
  await deleteArchiveFonds(row.id);
  ElMessage.success("已删除");
  await loadRows();
}

onMounted(loadRows);
</script>

<template>
  <section class="archive-fonds-page">
    <header class="archive-fonds-page__toolbar">
      <el-button type="primary" @click="openCreate">新增全宗</el-button>
    </header>
    <section class="archive-fonds-page__body">
      <el-table v-loading="loading" :data="rows" height="100%">
        <el-table-column prop="fondsCode" label="全宗编码" width="180" />
        <el-table-column prop="fondsName" label="全宗名称" min-width="220" />
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑全宗' : '新增全宗'" width="520px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="全宗编码" required>
          <el-input v-model="form.fondsCode" />
        </el-form-item>
        <el-form-item label="全宗名称" required>
          <el-input v-model="form.fondsName" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.archive-fonds-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 20px;
}

.archive-fonds-page__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.archive-fonds-page__body {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--am-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--am-bg-surface);
}
</style>
