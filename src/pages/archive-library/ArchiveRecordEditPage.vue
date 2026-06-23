<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getArchiveRecord, listArchiveFonds, updateArchiveRecord } from "../../shared/api/archive";
import { errorMessage } from "../../shared/api/client";
import type {
  ArchiveFondsDto,
  ArchiveRecordDetailDto,
  ArchiveRecordUpdateCommand,
} from "../../shared/types/archive";
import ArchiveRecordDynamicFields from "./ArchiveRecordDynamicFields.vue";

defineOptions({ name: "ArchiveRecordEditPage" });

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const detail = ref<ArchiveRecordDetailDto>();
const fonds = ref<ArchiveFondsDto[]>([]);

const form = reactive<ArchiveRecordUpdateCommand>({
  fondsCode: "",
  archiveNo: "",
  archiveYear: new Date().getFullYear(),
  electronicStatus: "DRAFT",
  physicalObject: {
    physicalStatus: "NONE",
    boxNo: "",
    locationNo: "",
    barcode: "",
    remark: "",
  },
  dynamicFields: {},
});

const recordId = computed(() => String(route.params.id));
const disabled = computed(() => detail.value?.record.lockedFlag === true || saving.value);

function resetForm(recordDetail: ArchiveRecordDetailDto) {
  form.fondsCode = recordDetail.record.fondsCode;
  form.archiveNo = recordDetail.record.archiveNo ?? "";
  form.archiveYear = recordDetail.record.archiveYear;
  form.electronicStatus = recordDetail.record.electronicStatus;
  form.physicalObject = {
    physicalStatus: recordDetail.physicalObject?.physicalStatus ?? "NONE",
    boxNo: recordDetail.physicalObject?.boxNo ?? "",
    locationNo: recordDetail.physicalObject?.locationNo ?? "",
    barcode: recordDetail.physicalObject?.barcode ?? "",
    remark: recordDetail.physicalObject?.remark ?? "",
  };
  form.dynamicFields = { ...recordDetail.dynamicFields };
}

async function loadDetail() {
  if (!recordId.value) {
    return;
  }
  loading.value = true;
  try {
    const [recordDetail, fondsRows] = await Promise.all([
      getArchiveRecord(recordId.value, "edit"),
      listArchiveFonds(true),
    ]);
    detail.value = recordDetail;
    fonds.value = fondsRows;
    resetForm(recordDetail);
  } catch (error) {
    ElMessage.error(errorMessage(error, "档案编辑信息加载失败"));
  } finally {
    loading.value = false;
  }
}

function updateDynamicField(fieldCode: string, value: unknown) {
  form.dynamicFields[fieldCode] = value;
}

async function submit() {
  if (!detail.value) {
    return;
  }
  if (detail.value.record.lockedFlag) {
    ElMessage.warning("当前档案已锁定，不能编辑");
    return;
  }
  saving.value = true;
  try {
    await updateArchiveRecord(recordId.value, form);
    ElMessage.success("已保存");
    await router.push({ name: "ArchiveRecordDetail", params: { id: recordId.value } });
  } catch (error) {
    ElMessage.error(errorMessage(error, "保存失败"));
  } finally {
    saving.value = false;
  }
}

watch(() => route.params.id, loadDetail, { immediate: true });
</script>

<template>
  <section class="archive-record-edit-page">
    <header class="archive-record-edit-page__toolbar">
      <el-button @click="router.back()">返回</el-button>
      <el-button type="primary" :loading="saving" :disabled="disabled" @click="submit">
        保存
      </el-button>
    </header>

    <section v-loading="loading" class="archive-record-edit-page__body">
      <el-alert
        v-if="detail?.record.lockedFlag"
        :title="detail.record.lockReason || '当前档案已锁定，不能编辑'"
        type="warning"
        show-icon
        :closable="false"
        class="archive-record-edit-page__alert"
      />
      <el-form v-if="detail" :model="form" label-position="top" :disabled="disabled">
        <section class="archive-record-edit-page__section">
          <div class="archive-record-edit-page__section-title">基础信息</div>
          <div class="archive-record-edit-page__fixed-fields">
            <el-form-item label="档案分类">
              <el-input :model-value="detail.category.categoryName" disabled />
            </el-form-item>
            <el-form-item label="全宗" required>
              <el-select v-model="form.fondsCode">
                <el-option
                  v-for="item in fonds"
                  :key="item.id"
                  :label="item.fondsName"
                  :value="item.fondsCode"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="档号">
              <el-input v-model="form.archiveNo" />
            </el-form-item>
            <el-form-item label="年度">
              <el-input-number v-model="form.archiveYear" :min="1" />
            </el-form-item>
            <el-form-item label="电子状态">
              <el-select v-model="form.electronicStatus">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已归档" value="ARCHIVED" />
                <el-option label="借阅中" value="BORROWED" />
              </el-select>
            </el-form-item>
            <el-form-item label="实物状态">
              <el-select v-model="form.physicalObject.physicalStatus">
                <el-option label="无实物" value="NONE" />
                <el-option label="已登记" value="REGISTERED" />
                <el-option label="移交中" value="TRANSFERRING" />
                <el-option label="已入库" value="IN_STORAGE" />
                <el-option label="借阅中" value="BORROWED" />
              </el-select>
            </el-form-item>
            <el-form-item label="盒号">
              <el-input v-model="form.physicalObject.boxNo" />
            </el-form-item>
            <el-form-item label="库位号">
              <el-input v-model="form.physicalObject.locationNo" />
            </el-form-item>
            <el-form-item label="条码">
              <el-input v-model="form.physicalObject.barcode" />
            </el-form-item>
            <el-form-item label="实物备注">
              <el-input v-model="form.physicalObject.remark" type="textarea" :rows="2" />
            </el-form-item>
          </div>
        </section>

        <section class="archive-record-edit-page__section">
          <div class="archive-record-edit-page__section-title">字段信息</div>
          <archive-record-dynamic-fields
            :fields="detail.fields"
            :model-value="form.dynamicFields"
            mode="edit"
            @update:field="updateDynamicField"
          />
        </section>
      </el-form>
    </section>
  </section>
</template>

<style scoped lang="scss">
.archive-record-edit-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 20px;
}

.archive-record-edit-page__toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-bottom: 16px;
}

.archive-record-edit-page__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--am-border);
  border-radius: 8px;
  padding: 16px;
  background: var(--am-bg-surface);
}

.archive-record-edit-page__alert {
  margin-bottom: 16px;
}

.archive-record-edit-page__section + .archive-record-edit-page__section {
  margin-top: 22px;
}

.archive-record-edit-page__section-title {
  margin-bottom: 12px;
  color: var(--am-text);
  font-weight: 600;
}

.archive-record-edit-page__fixed-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
}

:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
}
</style>
