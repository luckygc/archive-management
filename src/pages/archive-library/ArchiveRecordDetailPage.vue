<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  getArchiveRecord,
  listArchiveFonds,
  lockArchiveRecord,
  unlockArchiveRecord,
} from "../../shared/api/archive";
import { errorMessage } from "../../shared/api/client";
import type { ArchiveFondsDto, ArchiveRecordDetailDto } from "../../shared/types/archive";
import ArchiveRecordDynamicFields from "./ArchiveRecordDynamicFields.vue";

defineOptions({ name: "ArchiveRecordDetailPage" });

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref<ArchiveRecordDetailDto>();
const fonds = ref<ArchiveFondsDto[]>([]);

const recordId = computed(() => String(route.params.id));

const fondsName = computed(() => {
  const fondsCode = detail.value?.record.fondsCode;
  return fonds.value.find((item) => item.fondsCode === fondsCode)?.fondsName ?? fondsCode ?? "";
});

function electronicStatusText(value?: string) {
  if (value === "DRAFT") {
    return "草稿";
  }
  if (value === "ARCHIVED") {
    return "已归档";
  }
  if (value === "BORROWED") {
    return "借阅中";
  }
  return value ?? "-";
}

function physicalStatusText(value?: string) {
  if (value === "NONE") {
    return "无实物";
  }
  if (value === "REGISTERED") {
    return "已登记";
  }
  if (value === "TRANSFERRING") {
    return "移交中";
  }
  if (value === "IN_STORAGE") {
    return "已入库";
  }
  if (value === "BORROWED") {
    return "借阅中";
  }
  return value ?? "-";
}

async function loadDetail() {
  if (!recordId.value) {
    return;
  }
  loading.value = true;
  try {
    const [recordDetail, fondsRows] = await Promise.all([
      getArchiveRecord(recordId.value),
      listArchiveFonds(true),
    ]);
    detail.value = recordDetail;
    fonds.value = fondsRows;
  } catch (error) {
    ElMessage.error(errorMessage(error, "档案详情加载失败"));
  } finally {
    loading.value = false;
  }
}

async function openEdit() {
  await router.push({ name: "ArchiveRecordEdit", params: { id: recordId.value } });
}

async function lockCurrentRecord() {
  const { value } = await ElMessageBox.prompt("锁定原因", "锁定档案", {
    inputType: "textarea",
  });
  try {
    await lockArchiveRecord(recordId.value, value);
    ElMessage.success("已锁定");
    await loadDetail();
  } catch (error) {
    ElMessage.error(errorMessage(error, "锁定失败"));
  }
}

async function unlockCurrentRecord() {
  await ElMessageBox.confirm("确定解锁该档案记录？", "解锁档案", {
    type: "warning",
  });
  try {
    await unlockArchiveRecord(recordId.value);
    ElMessage.success("已解锁");
    await loadDetail();
  } catch (error) {
    ElMessage.error(errorMessage(error, "解锁失败"));
  }
}

watch(() => route.params.id, loadDetail, { immediate: true });
</script>

<template>
  <section class="archive-record-detail-page">
    <header class="archive-record-detail-page__toolbar">
      <el-button @click="router.back()">返回</el-button>
      <el-button type="primary" :disabled="detail?.record.lockedFlag" @click="openEdit">
        编辑
      </el-button>
      <el-button v-if="detail?.record.lockedFlag" type="primary" plain @click="unlockCurrentRecord">
        解锁
      </el-button>
      <el-button v-else type="primary" plain :disabled="!detail" @click="lockCurrentRecord">
        锁定
      </el-button>
    </header>

    <section v-loading="loading" class="archive-record-detail-page__body">
      <template v-if="detail">
        <el-alert
          v-if="detail.record.lockedFlag"
          :title="detail.record.lockReason || '当前档案已锁定'"
          type="warning"
          show-icon
          :closable="false"
          class="archive-record-detail-page__alert"
        />
        <section class="archive-record-detail-page__section">
          <div class="archive-record-detail-page__section-title">基础信息</div>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="档号">
              {{ detail.record.archiveNo || "-" }}
            </el-descriptions-item>
            <el-descriptions-item label="全宗">
              {{ fondsName || "-" }}
            </el-descriptions-item>
            <el-descriptions-item label="分类">
              {{ detail.category.categoryName }}
            </el-descriptions-item>
            <el-descriptions-item label="年度">
              {{ detail.record.archiveYear }}
            </el-descriptions-item>
            <el-descriptions-item label="电子状态">
              {{ electronicStatusText(detail.record.electronicStatus) }}
            </el-descriptions-item>
            <el-descriptions-item label="实物状态">
              {{ physicalStatusText(detail.physicalObject?.physicalStatus) }}
            </el-descriptions-item>
            <el-descriptions-item label="盒号">
              {{ detail.physicalObject?.boxNo || "-" }}
            </el-descriptions-item>
            <el-descriptions-item label="库位号">
              {{ detail.physicalObject?.locationNo || "-" }}
            </el-descriptions-item>
            <el-descriptions-item label="条码">
              {{ detail.physicalObject?.barcode || "-" }}
            </el-descriptions-item>
            <el-descriptions-item label="实物备注">
              {{ detail.physicalObject?.remark || "-" }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="archive-record-detail-page__section">
          <div class="archive-record-detail-page__section-title">字段信息</div>
          <archive-record-dynamic-fields
            :fields="detail.fields"
            :model-value="detail.dynamicFields"
            mode="detail"
          />
        </section>
      </template>
    </section>
  </section>
</template>

<style scoped lang="scss">
.archive-record-detail-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 20px;
}

.archive-record-detail-page__toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-bottom: 16px;
}

.archive-record-detail-page__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--am-border);
  border-radius: 8px;
  padding: 16px;
  background: var(--am-bg-surface);
}

.archive-record-detail-page__alert {
  margin-bottom: 16px;
}

.archive-record-detail-page__section + .archive-record-detail-page__section {
  margin-top: 22px;
}

.archive-record-detail-page__section-title {
  margin-bottom: 12px;
  color: var(--am-text);
  font-weight: 600;
}
</style>
