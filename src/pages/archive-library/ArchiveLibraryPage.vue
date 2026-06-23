<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  createArchiveRecord,
  listArchiveCategories,
  listArchiveFields,
  listArchiveFonds,
  lockArchiveRecord,
  searchArchiveRecords,
  unlockArchiveRecord,
} from "../../shared/api/archive";
import { errorMessage } from "../../shared/api/client";
import type {
  ArchiveCategoryDto,
  ArchiveFieldDto,
  ArchiveFieldType,
  ArchiveFondsDto,
  ArchiveRecordCommand,
  ArchiveRecordFieldFilter,
} from "../../shared/types/archive";
import ArchiveRecordDynamicFields from "./ArchiveRecordDynamicFields.vue";

defineOptions({ name: "ArchiveLibraryPage" });

interface CategorySelectNode {
  value: string;
  label: string;
  disabled?: boolean;
  children?: CategorySelectNode[];
}

interface AdvancedFilterValue {
  value?: unknown;
  startValue?: unknown;
  endValue?: unknown;
}

const loading = ref(false);
const saving = ref(false);
const drawerVisible = ref(false);
const advancedVisible = ref(false);
const router = useRouter();
const categories = ref<ArchiveCategoryDto[]>([]);
const fonds = ref<ArchiveFondsDto[]>([]);
const fields = ref<ArchiveFieldDto[]>([]);
const formFields = ref<ArchiveFieldDto[]>([]);
const rows = ref<Record<string, unknown>[]>([]);
const selectedCategoryId = ref<string>();
const selectedFondsCode = ref("");
const keyword = ref("");
const tableBuilt = ref(true);
const advancedFilters = ref<Record<string, AdvancedFilterValue>>({});

const form = reactive<ArchiveRecordCommand>({
  categoryId: "",
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

const categoryOptions = computed(() => buildCategoryOptions(categories.value));

const selectedCategory = computed(() =>
  categories.value.find((item) => item.id === selectedCategoryId.value),
);

const searchFields = computed(() => fields.value.filter((field) => field.exactSearchable));

const listFields = computed(() =>
  [...fields.value]
    .filter((field) => field.listVisible)
    .sort(
      (current, next) =>
        current.listSortOrder - next.listSortOrder || current.id.localeCompare(next.id),
    ),
);

const activeAdvancedFilterCount = computed(() => buildAdvancedFilters().length);

function buildCategoryOptions(rows: ArchiveCategoryDto[]) {
  const nodeMap = new Map<string, CategorySelectNode>();
  const roots: CategorySelectNode[] = [];
  for (const row of rows.filter((item) => item.enabled)) {
    nodeMap.set(row.id, {
      value: row.id,
      label: row.categoryName,
      disabled: row.tableStatus !== "built",
      children: [],
    });
  }
  for (const row of rows.filter((item) => item.enabled)) {
    const node = nodeMap.get(row.id);
    if (!node) {
      continue;
    }
    const parent = row.parentId ? nodeMap.get(row.parentId) : undefined;
    if (parent) {
      parent.children?.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}

function firstBuiltCategoryId(nodes: CategorySelectNode[]): string | undefined {
  for (const node of nodes) {
    if (!node.disabled) {
      return node.value;
    }
    const childId = node.children ? firstBuiltCategoryId(node.children) : undefined;
    if (childId) {
      return childId;
    }
  }
  return undefined;
}

function rowValue(row: Record<string, unknown>, key: string) {
  return row[key] ?? "";
}

function fixedRowValue(row: Record<string, unknown>, key: string) {
  return row[key] ?? row[toCamelCase(key)] ?? "";
}

function rowFondsName(row: Record<string, unknown>) {
  const code = fixedRowValue(row, "fonds_code");
  return fonds.value.find((item) => item.fondsCode === code)?.fondsName ?? code ?? "";
}

function isLocked(row: Record<string, unknown>) {
  return fixedRowValue(row, "locked_flag") === true;
}

function rowId(row: Record<string, unknown>) {
  return String(fixedRowValue(row, "id"));
}

function fieldFilter(field: ArchiveFieldDto) {
  advancedFilters.value[field.fieldCode] ??= {};
  return advancedFilters.value[field.fieldCode];
}

function isRangeField(fieldType: ArchiveFieldType) {
  return (
    fieldType === "integer" ||
    fieldType === "decimal" ||
    fieldType === "date" ||
    fieldType === "datetime"
  );
}

function isDateRangeField(fieldType: ArchiveFieldType) {
  return fieldType === "date" || fieldType === "datetime";
}

function dateInputType(field: ArchiveFieldDto) {
  return field.fieldType === "datetime" ? "datetime" : "date";
}

function dateRangeFormat(field: ArchiveFieldDto) {
  return field.fieldType === "datetime" ? "YYYY-MM-DDTHH:mm:ss" : "YYYY-MM-DD";
}

function hasFilterValue(value: unknown) {
  return value !== undefined && value !== null && value !== "";
}

function buildAdvancedFilters(): ArchiveRecordFieldFilter[] {
  return searchFields.value
    .map((field) => {
      const filter = advancedFilters.value[field.fieldCode] ?? {};
      if (isRangeField(field.fieldType)) {
        return {
          fieldCode: field.fieldCode,
          startValue: filter.startValue,
          endValue: filter.endValue,
        };
      }
      return {
        fieldCode: field.fieldCode,
        value: filter.value,
      };
    })
    .filter(
      (filter) =>
        hasFilterValue(filter.value) ||
        hasFilterValue(filter.startValue) ||
        hasFilterValue(filter.endValue),
    );
}

function toCamelCase(value: string) {
  return value.replace(/_([a-z0-9])/g, (_, char: string) => char.toUpperCase());
}

function resetForm() {
  form.categoryId = selectedCategoryId.value ?? "";
  form.fondsCode = selectedFondsCode.value || fonds.value[0]?.fondsCode || "";
  form.archiveNo = "";
  form.archiveYear = new Date().getFullYear();
  form.electronicStatus = "DRAFT";
  form.physicalObject = {
    physicalStatus: "NONE",
    boxNo: "",
    locationNo: "",
    barcode: "",
    remark: "",
  };
  form.dynamicFields = {};
  for (const field of formFields.value) {
    form.dynamicFields[field.fieldCode] = undefined;
  }
}

async function loadOptions() {
  const [categoryRows, fondsRows] = await Promise.all([
    listArchiveCategories(true),
    listArchiveFonds(true),
  ]);
  categories.value = categoryRows;
  fonds.value = fondsRows;
  if (!selectedCategoryId.value) {
    selectedCategoryId.value = firstBuiltCategoryId(categoryOptions.value);
  }
}

async function loadRecords() {
  loading.value = true;
  try {
    const result = await searchArchiveRecords({
      categoryId: selectedCategoryId.value,
      fondsCode: selectedFondsCode.value,
      keyword: keyword.value,
      filters: buildAdvancedFilters(),
    });
    fields.value = result.fields;
    rows.value = result.rows;
    tableBuilt.value = result.tableBuilt;
  } catch (error) {
    ElMessage.error(errorMessage(error, "档案列表加载失败"));
  } finally {
    loading.value = false;
  }
}

async function openCreate() {
  if (!selectedCategoryId.value) {
    ElMessage.warning("请先选择已建表的档案分类");
    return;
  }
  try {
    formFields.value = (await listArchiveFields(selectedCategoryId.value)).filter(
      (field) => field.enabled,
    );
  } catch (error) {
    ElMessage.error(errorMessage(error, "字段加载失败"));
    return;
  }
  resetForm();
  drawerVisible.value = true;
}

async function submit() {
  if (!form.categoryId || !form.fondsCode) {
    ElMessage.warning("请选择分类和全宗");
    return;
  }
  saving.value = true;
  try {
    const createdCategoryId = form.categoryId;
    const createdFondsCode = form.fondsCode;
    await createArchiveRecord(form);
    ElMessage.success("已保存");
    drawerVisible.value = false;
    keyword.value = "";
    advancedFilters.value = {};
    selectedCategoryId.value = createdCategoryId;
    selectedFondsCode.value = createdFondsCode;
    await loadRecords();
  } catch (error) {
    ElMessage.error(errorMessage(error, "保存失败"));
  } finally {
    saving.value = false;
  }
}

async function lockRecord(row: Record<string, unknown>) {
  const id = rowId(row);
  const { value } = await ElMessageBox.prompt("锁定原因", "锁定档案", {
    inputType: "textarea",
  });
  try {
    await lockArchiveRecord(id, value);
    ElMessage.success("已锁定");
    await loadRecords();
  } catch (error) {
    ElMessage.error(errorMessage(error, "锁定失败"));
  }
}

async function unlockRecord(row: Record<string, unknown>) {
  const id = rowId(row);
  await ElMessageBox.confirm("确定解锁该档案记录？", "解锁档案", {
    type: "warning",
  });
  try {
    await unlockArchiveRecord(id);
    ElMessage.success("已解锁");
    await loadRecords();
  } catch (error) {
    ElMessage.error(errorMessage(error, "解锁失败"));
  }
}

async function openDetail(row: Record<string, unknown>) {
  await router.push({ name: "ArchiveRecordDetail", params: { id: rowId(row) } });
}

async function openEdit(row: Record<string, unknown>) {
  await router.push({ name: "ArchiveRecordEdit", params: { id: rowId(row) } });
}

function updateDynamicField(fieldCode: string, value: unknown) {
  form.dynamicFields[fieldCode] = value;
}

async function resetSearch() {
  keyword.value = "";
  advancedFilters.value = {};
  await loadRecords();
}

watch(selectedCategoryId, () => {
  advancedFilters.value = {};
});

watch([selectedCategoryId, selectedFondsCode], loadRecords);

onMounted(async () => {
  await loadOptions();
  await loadRecords();
});
</script>

<template>
  <section class="archive-library-page">
    <header class="archive-library-page__toolbar">
      <el-tree-select
        v-model="selectedCategoryId"
        :data="categoryOptions"
        placeholder="选择档案分类"
        clearable
        check-strictly
        default-expand-all
        class="archive-library-page__select"
      />
      <el-select
        v-model="selectedFondsCode"
        placeholder="全部全宗"
        clearable
        class="archive-library-page__select"
      >
        <el-option
          v-for="item in fonds"
          :key="item.id"
          :label="item.fondsName"
          :value="item.fondsCode"
        />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="全文关键词"
        class="archive-library-page__select"
        @keyup.enter="loadRecords"
        @clear="loadRecords"
      />
      <el-button @click="loadRecords">查询</el-button>
      <el-button @click="resetSearch">重置</el-button>
      <el-button :disabled="searchFields.length === 0" @click="advancedVisible = !advancedVisible">
        高级筛选{{ activeAdvancedFilterCount ? ` ${activeAdvancedFilterCount}` : "" }}
      </el-button>
      <el-button type="primary" :disabled="!selectedCategoryId" @click="openCreate">
        新增档案
      </el-button>
    </header>
    <section v-if="advancedVisible" class="archive-library-page__advanced">
      <el-empty v-if="searchFields.length === 0" description="当前分类没有可筛选字段" />
      <el-form v-else label-position="top">
        <div class="archive-library-page__advanced-grid">
          <el-form-item v-for="field in searchFields" :key="field.id" :label="field.fieldName">
            <template v-if="isRangeField(field.fieldType)">
              <div class="archive-library-page__range">
                <template v-if="isDateRangeField(field.fieldType)">
                  <el-date-picker
                    :model-value="fieldFilter(field).startValue as string"
                    :type="dateInputType(field)"
                    :value-format="dateRangeFormat(field)"
                    placeholder="开始"
                    @update:model-value="
                      (value: string | undefined) => (fieldFilter(field).startValue = value)
                    "
                  />
                  <el-date-picker
                    :model-value="fieldFilter(field).endValue as string"
                    :type="dateInputType(field)"
                    :value-format="dateRangeFormat(field)"
                    placeholder="结束"
                    @update:model-value="
                      (value: string | undefined) => (fieldFilter(field).endValue = value)
                    "
                  />
                </template>
                <template v-else>
                  <el-input-number
                    :model-value="fieldFilter(field).startValue as number"
                    placeholder="最小值"
                    @update:model-value="
                      (value: number | undefined) => (fieldFilter(field).startValue = value)
                    "
                  />
                  <el-input-number
                    :model-value="fieldFilter(field).endValue as number"
                    placeholder="最大值"
                    @update:model-value="
                      (value: number | undefined) => (fieldFilter(field).endValue = value)
                    "
                  />
                </template>
              </div>
            </template>
            <el-input
              v-else
              :model-value="fieldFilter(field).value as string"
              clearable
              @update:model-value="(value: string) => (fieldFilter(field).value = value)"
              @keyup.enter="loadRecords"
            />
          </el-form-item>
        </div>
      </el-form>
    </section>
    <section class="archive-library-page__body">
      <el-table v-loading="loading" :data="rows" height="100%">
        <el-table-column label="档号" width="160">
          <template #default="{ row }">{{ fixedRowValue(row, "archive_no") }}</template>
        </el-table-column>
        <el-table-column prop="fonds_code" label="全宗" width="160">
          <template #default="{ row }">{{ rowFondsName(row) }}</template>
        </el-table-column>
        <el-table-column label="分类" width="150">
          <template #default="{ row }">{{ fixedRowValue(row, "category_name") }}</template>
        </el-table-column>
        <el-table-column label="年度" width="90">
          <template #default="{ row }">{{ fixedRowValue(row, "archive_year") }}</template>
        </el-table-column>
        <el-table-column label="锁定" width="90">
          <template #default="{ row }">
            <el-tag :type="isLocked(row) ? 'warning' : 'info'">
              {{ isLocked(row) ? "已锁定" : "未锁定" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-for="field in listFields"
          :key="field.id"
          :label="field.fieldName"
          :min-width="field.listWidth ?? 150"
        >
          <template #default="{ row }">
            {{ rowValue(row, field.columnName) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" :disabled="isLocked(row)" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-if="isLocked(row)" link type="primary" @click="unlockRecord(row)">
              解锁
            </el-button>
            <el-button v-else link type="primary" @click="lockRecord(row)">锁定</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!loading && selectedCategory && !tableBuilt"
        description="当前分类尚未生成数据表"
      />
    </section>

    <el-drawer v-model="drawerVisible" title="新增档案" size="520px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="档案分类">
          <el-input :model-value="selectedCategory?.categoryName" disabled />
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
        <archive-record-dynamic-fields
          :fields="formFields"
          :model-value="form.dynamicFields"
          mode="edit"
          @update:field="updateDynamicField"
        />
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-drawer>
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
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  margin-bottom: 16px;
}

.archive-library-page__select {
  width: 220px;
}

.archive-library-page__advanced {
  margin-bottom: 16px;
  border: 1px solid var(--am-border);
  border-radius: 8px;
  padding: 16px;
  background: var(--am-bg-surface);
}

.archive-library-page__advanced-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px 18px;
}

.archive-library-page__range {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.archive-library-page__advanced :deep(.el-form-item) {
  margin-bottom: 0;
}

.archive-library-page__advanced :deep(.el-date-editor),
.archive-library-page__advanced :deep(.el-input-number) {
  width: 100%;
}

.archive-library-page__body {
  position: relative;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--am-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--am-bg-surface);
}
</style>
