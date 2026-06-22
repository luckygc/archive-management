<script setup lang="ts">
import { Rank } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { VueDraggable } from "vue-draggable-plus";
import {
  buildArchiveCategoryTable,
  createArchiveCategory,
  createArchiveField,
  createArchiveUniqueConstraint,
  deleteArchiveCategory,
  deleteArchiveField,
  deleteArchiveUniqueConstraint,
  getArchiveCategoryLayout,
  listArchiveCategories,
  listArchiveFields,
  listArchiveUniqueConstraints,
  saveMyArchiveCategoryLayout,
  savePublicArchiveCategoryLayout,
  updateArchiveCategory,
  updateArchiveField,
  updateArchiveUniqueConstraint,
} from "../../shared/api/archive";
import type {
  ArchiveCategoryCommand,
  ArchiveCategoryDto,
  ArchiveFieldControl,
  ArchiveFieldCommand,
  ArchiveFieldDto,
  ArchiveFieldLayoutItemDto,
  ArchiveLevel,
  ArchiveLayoutScope,
  ArchiveLayoutSurface,
  ArchiveFieldType,
  ArchiveManagementMode,
  ArchiveUniqueConstraintCommand,
  ArchiveUniqueConstraintDto,
} from "../../shared/types/archive";

defineOptions({ name: "ArchiveCategoriesPage" });

interface CategoryTreeNode extends ArchiveCategoryDto {
  children?: CategoryTreeNode[];
}

interface CategorySelectNode {
  value: number;
  label: string;
  children?: CategorySelectNode[];
}

const fieldTypeOptions: Array<{ label: string; value: ArchiveFieldType }> = [
  { label: "文本", value: "TEXT" },
  { label: "整数", value: "INTEGER" },
  { label: "小数", value: "DECIMAL" },
  { label: "日期", value: "DATE" },
  { label: "日期时间", value: "DATETIME" },
];

const fieldControlLabels: Record<ArchiveFieldControl, string> = {
  INPUT: "单行输入",
  TEXTAREA: "多行文本",
  NUMBER: "数字输入",
  DATE: "日期选择",
  DATETIME: "日期时间",
};

const layoutSurfaceOptions: Array<{ label: string; value: ArchiveLayoutSurface }> = [
  { label: "表格", value: "TABLE" },
  { label: "详情", value: "DETAIL" },
  { label: "编辑", value: "EDIT" },
];

const layoutScopeOptions: Array<{ label: string; value: ArchiveLayoutScope }> = [
  { label: "公共布局", value: "PUBLIC" },
  { label: "我的布局", value: "MINE" },
];

const archiveLevelOptions: Array<{ label: string; value: ArchiveLevel }> = [
  { label: "卷内", value: "ITEM" },
  { label: "案卷", value: "VOLUME" },
];

const managementModeOptions: Array<{ label: string; value: ArchiveManagementMode }> = [
  { label: "按条目管理", value: "ITEM_ONLY" },
  { label: "按案卷/卷内管理", value: "VOLUME_ITEM" },
];

const categoriesLoading = ref(false);
const fieldsLoading = ref(false);
const constraintsLoading = ref(false);
const saving = ref(false);
const building = ref(false);
const categoryDialogVisible = ref(false);
const fieldDialogVisible = ref(false);
const constraintDialogVisible = ref(false);
const activeConfigTab = ref("fields");
const activeArchiveLevel = ref<ArchiveLevel>("ITEM");
const activeLayoutSurface = ref<ArchiveLayoutSurface>("TABLE");
const activeLayoutScope = ref<ArchiveLayoutScope>("MINE");
const layoutLoading = ref(false);
const editingCategoryId = ref<number>();
const editingFieldId = ref<number>();
const editingConstraintId = ref<number>();
const selectedCategoryId = ref<number>();
const categories = ref<ArchiveCategoryDto[]>([]);
const fields = ref<ArchiveFieldDto[]>([]);
const layoutItems = ref<ArchiveFieldLayoutItemDto[]>([]);
const uniqueConstraints = ref<ArchiveUniqueConstraintDto[]>([]);

const categoryForm = reactive<ArchiveCategoryCommand>({
  categoryCode: "",
  categoryName: "",
  parentId: undefined,
  managementMode: "ITEM_ONLY",
  enabled: true,
  sortOrder: 0,
});

const fieldForm = reactive<ArchiveFieldCommand>({
  archiveLevel: "ITEM",
  fieldCode: "",
  fieldName: "",
  fieldType: "TEXT",
  textLength: 500,
  decimalPrecision: 18,
  decimalScale: 2,
  editControl: "INPUT",
  listVisible: true,
  listWidth: undefined,
  listSortOrder: 0,
  detailVisible: true,
  detailColSpan: 1,
  detailSortOrder: 0,
  editVisible: true,
  editColSpan: 1,
  editSortOrder: 0,
  exactSearchable: false,
  fullTextSearchable: false,
  enabled: true,
  sortOrder: 0,
});

const constraintForm = reactive<ArchiveUniqueConstraintCommand>({
  archiveLevel: "ITEM",
  constraintCode: "",
  constraintName: "",
  includeFonds: true,
  enabled: true,
  fieldIds: [],
});

const selectedCategory = computed(() =>
  categories.value.find((item) => item.id === selectedCategoryId.value),
);

const categoryTree = computed(() => buildCategoryTree(categories.value));

const parentOptions = computed(() =>
  buildCategorySelectTree(
    categories.value.filter((item) => item.id !== editingCategoryId.value),
    editingCategoryId.value,
  ),
);

const currentLevelFields = computed(() =>
  fields.value.filter((field) => field.archiveLevel === activeArchiveLevel.value),
);

const currentLevelConstraints = computed(() =>
  uniqueConstraints.value.filter((item) => item.archiveLevel === activeArchiveLevel.value),
);

const currentLevelTableName = computed(() =>
  activeArchiveLevel.value === "VOLUME"
    ? selectedCategory.value?.volumeTableName
    : selectedCategory.value?.itemTableName,
);

function tableStatusText(row: ArchiveCategoryDto) {
  return row.tableStatus === "BUILT" ? "已建表" : "未建表";
}

function fieldControlText(value: ArchiveFieldControl) {
  return fieldControlLabels[value];
}

function fieldControlOptions(fieldType: ArchiveFieldType) {
  if (fieldType === "TEXT") {
    return ["INPUT", "TEXTAREA"] satisfies ArchiveFieldControl[];
  }
  if (fieldType === "INTEGER" || fieldType === "DECIMAL") {
    return ["NUMBER"] satisfies ArchiveFieldControl[];
  }
  if (fieldType === "DATE") {
    return ["DATE"] satisfies ArchiveFieldControl[];
  }
  return ["DATETIME"] satisfies ArchiveFieldControl[];
}

function defaultFieldControl(fieldType: ArchiveFieldType): ArchiveFieldControl {
  return fieldControlOptions(fieldType)[0];
}

function buildCategoryTree(rows: ArchiveCategoryDto[]) {
  const nodeMap = new Map<number, CategoryTreeNode>();
  const roots: CategoryTreeNode[] = [];
  for (const row of rows) {
    nodeMap.set(row.id, { ...row, children: [] });
  }
  for (const row of rows) {
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

function buildCategorySelectTree(rows: ArchiveCategoryDto[], editingId?: number) {
  const excludedIds = editingId ? collectDescendantIds(rows, editingId) : new Set<number>();
  const availableRows = rows.filter((row) => !excludedIds.has(row.id));
  return buildCategoryTree(availableRows).map(toSelectNode);
}

function collectDescendantIds(rows: ArchiveCategoryDto[], parentId: number) {
  const childrenByParent = new Map<number, ArchiveCategoryDto[]>();
  for (const row of rows) {
    if (!row.parentId) {
      continue;
    }
    childrenByParent.set(row.parentId, [...(childrenByParent.get(row.parentId) ?? []), row]);
  }
  const ids = new Set<number>();
  const stack = [...(childrenByParent.get(parentId) ?? [])];
  while (stack.length > 0) {
    const current = stack.pop();
    if (!current) {
      continue;
    }
    ids.add(current.id);
    stack.push(...(childrenByParent.get(current.id) ?? []));
  }
  return ids;
}

function toSelectNode(row: CategoryTreeNode): CategorySelectNode {
  return {
    value: row.id,
    label: row.categoryName,
    children: row.children?.length ? row.children.map(toSelectNode) : undefined,
  };
}

function resetCategoryForm(row?: ArchiveCategoryDto) {
  editingCategoryId.value = row?.id;
  categoryForm.categoryCode = row?.categoryCode ?? "";
  categoryForm.categoryName = row?.categoryName ?? "";
  categoryForm.parentId = row?.parentId;
  categoryForm.managementMode = row?.managementMode ?? "ITEM_ONLY";
  categoryForm.enabled = row?.enabled ?? true;
  categoryForm.sortOrder = row?.sortOrder ?? 0;
}

function resetFieldForm(row?: ArchiveFieldDto) {
  editingFieldId.value = row?.id;
  fieldForm.archiveLevel = row?.archiveLevel ?? activeArchiveLevel.value;
  fieldForm.fieldCode = row?.fieldCode ?? "";
  fieldForm.fieldName = row?.fieldName ?? "";
  fieldForm.fieldType = row?.fieldType ?? "TEXT";
  fieldForm.textLength = row?.textLength ?? 500;
  fieldForm.decimalPrecision = row?.decimalPrecision ?? 18;
  fieldForm.decimalScale = row?.decimalScale ?? 2;
  fieldForm.editControl = row?.editControl ?? defaultFieldControl(fieldForm.fieldType);
  fieldForm.listVisible = row?.listVisible ?? true;
  fieldForm.listWidth = row?.listWidth;
  fieldForm.listSortOrder = row?.listSortOrder ?? row?.sortOrder ?? 0;
  fieldForm.detailVisible = row?.detailVisible ?? true;
  fieldForm.detailColSpan = row?.detailColSpan ?? 1;
  fieldForm.detailSortOrder = row?.detailSortOrder ?? row?.sortOrder ?? 0;
  fieldForm.editVisible = row?.editVisible ?? true;
  fieldForm.editColSpan = row?.editColSpan ?? 1;
  fieldForm.editSortOrder = row?.editSortOrder ?? row?.sortOrder ?? 0;
  fieldForm.exactSearchable = row?.exactSearchable ?? false;
  fieldForm.fullTextSearchable = row?.fullTextSearchable ?? false;
  fieldForm.enabled = row?.enabled ?? true;
  fieldForm.sortOrder = row?.sortOrder ?? 0;
}

function resetConstraintForm(row?: ArchiveUniqueConstraintDto) {
  editingConstraintId.value = row?.id;
  constraintForm.archiveLevel = row?.archiveLevel ?? activeArchiveLevel.value;
  constraintForm.constraintCode = row?.constraintCode ?? "";
  constraintForm.constraintName = row?.constraintName ?? "";
  constraintForm.includeFonds = row?.includeFonds ?? true;
  constraintForm.enabled = row?.enabled ?? true;
  constraintForm.fieldIds = row?.fields.map((field) => field.fieldId) ?? [];
}

async function loadCategories() {
  categoriesLoading.value = true;
  try {
    categories.value = await listArchiveCategories();
    if (!selectedCategoryId.value && categories.value.length > 0) {
      selectedCategoryId.value = categories.value[0].id;
      await loadFields();
      await loadUniqueConstraints();
    }
  } finally {
    categoriesLoading.value = false;
  }
}

async function loadFields() {
  if (!selectedCategoryId.value) {
    fields.value = [];
    uniqueConstraints.value = [];
    return;
  }
  fieldsLoading.value = true;
  try {
    fields.value = await listArchiveFields(selectedCategoryId.value);
  } finally {
    fieldsLoading.value = false;
  }
}

async function loadUniqueConstraints() {
  if (!selectedCategoryId.value) {
    uniqueConstraints.value = [];
    return;
  }
  constraintsLoading.value = true;
  try {
    uniqueConstraints.value = await listArchiveUniqueConstraints(selectedCategoryId.value);
  } finally {
    constraintsLoading.value = false;
  }
}

async function selectCategory(row: ArchiveCategoryDto) {
  selectedCategoryId.value = row.id;
  await loadFields();
  await loadUniqueConstraints();
}

function openCreateCategory() {
  resetCategoryForm();
  categoryDialogVisible.value = true;
}

function openEditCategory(row: ArchiveCategoryDto) {
  resetCategoryForm(row);
  categoryDialogVisible.value = true;
}

async function submitCategory() {
  saving.value = true;
  try {
    const saved = editingCategoryId.value
      ? await updateArchiveCategory(editingCategoryId.value, categoryForm)
      : await createArchiveCategory(categoryForm);
    selectedCategoryId.value = saved.id;
    ElMessage.success("已保存");
    categoryDialogVisible.value = false;
    await loadCategories();
    await loadFields();
  } finally {
    saving.value = false;
  }
}

async function removeCategory(row: ArchiveCategoryDto) {
  await ElMessageBox.confirm(`确定删除分类“${row.categoryName}”？`, "删除分类", {
    type: "warning",
  });
  await deleteArchiveCategory(row.id);
  ElMessage.success("已删除");
  if (selectedCategoryId.value === row.id) {
    selectedCategoryId.value = undefined;
    fields.value = [];
  }
  await loadCategories();
}

function openCreateField() {
  if (!selectedCategoryId.value) {
    ElMessage.warning("请先选择档案分类");
    return;
  }
  resetFieldForm();
  fieldDialogVisible.value = true;
}

function openEditField(row: ArchiveFieldDto) {
  resetFieldForm(row);
  fieldDialogVisible.value = true;
}

async function submitField() {
  if (!selectedCategoryId.value) {
    return;
  }
  saving.value = true;
  try {
    if (editingFieldId.value) {
      await updateArchiveField(selectedCategoryId.value, editingFieldId.value, fieldForm);
    } else {
      await createArchiveField(selectedCategoryId.value, fieldForm);
    }
    ElMessage.success("已保存");
    fieldDialogVisible.value = false;
    await loadFields();
  } finally {
    saving.value = false;
  }
}

async function removeField(row: ArchiveFieldDto) {
  if (!selectedCategoryId.value) {
    return;
  }
  await ElMessageBox.confirm(`确定删除字段“${row.fieldName}”？`, "删除字段", {
    type: "warning",
  });
  await deleteArchiveField(selectedCategoryId.value, row.id);
  ElMessage.success("已删除");
  await loadFields();
}

function openCreateConstraint() {
  if (!selectedCategoryId.value) {
    ElMessage.warning("请先选择档案分类");
    return;
  }
  resetConstraintForm();
  constraintDialogVisible.value = true;
}

function openEditConstraint(row: ArchiveUniqueConstraintDto) {
  resetConstraintForm(row);
  constraintDialogVisible.value = true;
}

async function submitConstraint() {
  if (!selectedCategoryId.value) {
    return;
  }
  if (constraintForm.fieldIds.length === 0) {
    ElMessage.warning("请选择唯一约束字段");
    return;
  }
  saving.value = true;
  try {
    if (editingConstraintId.value) {
      await updateArchiveUniqueConstraint(
        selectedCategoryId.value,
        editingConstraintId.value,
        constraintForm,
      );
    } else {
      await createArchiveUniqueConstraint(selectedCategoryId.value, constraintForm);
    }
    ElMessage.success("已保存");
    constraintDialogVisible.value = false;
    await loadUniqueConstraints();
  } finally {
    saving.value = false;
  }
}

async function removeConstraint(row: ArchiveUniqueConstraintDto) {
  if (!selectedCategoryId.value) {
    return;
  }
  await ElMessageBox.confirm(`确定删除唯一约束“${row.constraintName}”？`, "删除唯一约束", {
    type: "warning",
  });
  await deleteArchiveUniqueConstraint(selectedCategoryId.value, row.id);
  ElMessage.success("已删除");
  await loadUniqueConstraints();
}

function constraintFieldNames(row: ArchiveUniqueConstraintDto) {
  return row.fields.map((field) => field.fieldName).join(" + ");
}

async function loadLayout() {
  if (!selectedCategoryId.value || activeConfigTab.value !== "layout") {
    layoutItems.value = [];
    return;
  }
  layoutLoading.value = true;
  try {
    const layout = await getArchiveCategoryLayout(
      selectedCategoryId.value,
      activeLayoutSurface.value,
      activeLayoutScope.value,
      activeArchiveLevel.value,
    );
    layoutItems.value = layout.items;
  } finally {
    layoutLoading.value = false;
  }
}

function layoutCommand() {
  return {
    items: layoutItems.value.map((item, index) => ({
      fieldId: item.fieldId,
      visible: item.visible,
      listWidth: activeLayoutSurface.value === "TABLE" ? item.listWidth : undefined,
      colSpan: item.colSpan,
      rowOrder: index * 10,
      colOrder: 0,
    })),
  };
}

async function saveLayout() {
  if (!selectedCategoryId.value) {
    return;
  }
  saving.value = true;
  try {
    const layout =
      activeLayoutScope.value === "PUBLIC"
        ? await savePublicArchiveCategoryLayout(
            selectedCategoryId.value,
            activeLayoutSurface.value,
            layoutCommand(),
            activeArchiveLevel.value,
          )
        : await saveMyArchiveCategoryLayout(
            selectedCategoryId.value,
            activeLayoutSurface.value,
            layoutCommand(),
            activeArchiveLevel.value,
          );
    layoutItems.value = layout.items;
    ElMessage.success("布局已保存");
  } finally {
    saving.value = false;
  }
}

function layoutItemStyle(item: ArchiveFieldLayoutItemDto) {
  return activeLayoutSurface.value === "TABLE"
    ? undefined
    : { gridColumn: `span ${Math.min(Math.max(item.colSpan || 1, 1), 2)}` };
}

function layoutControlText(item: ArchiveFieldLayoutItemDto) {
  return fieldControlLabels[item.editControl];
}

async function buildTable() {
  if (!selectedCategoryId.value) {
    ElMessage.warning("请先选择档案分类");
    return;
  }
  building.value = true;
  try {
    const category = await buildArchiveCategoryTable(
      selectedCategoryId.value,
      activeArchiveLevel.value,
    );
    selectedCategoryId.value = category.id;
    ElMessage.success("建表完成");
    await loadCategories();
  } finally {
    building.value = false;
  }
}

watch(
  () => fieldForm.fieldType,
  (fieldType) => {
    if (!fieldControlOptions(fieldType).includes(fieldForm.editControl ?? "INPUT")) {
      fieldForm.editControl = defaultFieldControl(fieldType);
    }
  },
);

watch(
  [selectedCategoryId, activeConfigTab, activeArchiveLevel, activeLayoutSurface, activeLayoutScope],
  () => {
    void loadLayout();
  },
);

watch(activeArchiveLevel, (archiveLevel) => {
  if (!editingFieldId.value) {
    fieldForm.archiveLevel = archiveLevel;
  }
  if (!editingConstraintId.value) {
    constraintForm.archiveLevel = archiveLevel;
  }
});

onMounted(loadCategories);
</script>

<template>
  <section class="archive-categories-page">
    <section class="archive-categories-page__categories">
      <header class="archive-categories-page__toolbar">
        <el-button type="primary" @click="openCreateCategory">新增分类</el-button>
      </header>
      <el-table
        v-loading="categoriesLoading"
        :data="categoryTree"
        height="100%"
        default-expand-all
        highlight-current-row
        :current-row-key="selectedCategoryId"
        row-key="id"
        @row-click="selectCategory"
      >
        <el-table-column prop="categoryCode" label="分类编码" width="150" />
        <el-table-column prop="categoryName" label="分类名称" min-width="190" />
        <el-table-column prop="managementMode" label="管理模式" width="130">
          <template #default="{ row }">
            {{ row.managementMode === "VOLUME_ITEM" ? "案卷/卷内" : "条目" }}
          </template>
        </el-table-column>
        <el-table-column prop="tableStatus" label="建表状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.tableStatus === 'BUILT' ? 'success' : 'info'">
              {{ tableStatusText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openEditCategory(row)">编辑</el-button>
            <el-button link type="danger" @click.stop="removeCategory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="archive-categories-page__fields">
      <header class="archive-categories-page__toolbar">
        <div class="archive-categories-page__selected">
          {{ selectedCategory?.categoryName ?? "未选择分类" }}
          <span v-if="currentLevelTableName" class="archive-categories-page__table-name">
            {{ currentLevelTableName }}
          </span>
        </div>
        <div class="archive-categories-page__actions">
          <el-segmented v-model="activeArchiveLevel" :options="archiveLevelOptions" />
          <el-button :disabled="!selectedCategoryId" @click="openCreateField">新增字段</el-button>
          <el-button :disabled="!selectedCategoryId" @click="openCreateConstraint"
            >新增约束</el-button
          >
          <el-button
            type="primary"
            :loading="building"
            :disabled="!selectedCategoryId"
            @click="buildTable"
          >
            生成/更新表
          </el-button>
        </div>
      </header>
      <el-tabs v-model="activeConfigTab" class="archive-categories-page__tabs">
        <el-tab-pane label="字段定义" name="fields">
          <el-table v-loading="fieldsLoading" :data="currentLevelFields" height="100%">
            <el-table-column prop="archiveLevel" label="层级" width="80">
              <template #default="{ row }">{{
                row.archiveLevel === "VOLUME" ? "案卷" : "卷内"
              }}</template>
            </el-table-column>
            <el-table-column prop="fieldCode" label="字段编码" width="150" />
            <el-table-column prop="fieldName" label="字段名称" min-width="150" />
            <el-table-column prop="fieldType" label="类型" width="110" />
            <el-table-column prop="editControl" label="控件" width="110">
              <template #default="{ row }">{{ fieldControlText(row.editControl) }}</template>
            </el-table-column>
            <el-table-column prop="columnName" label="物理列" width="140" />
            <el-table-column prop="exactSearchable" label="精确" width="80">
              <template #default="{ row }">{{ row.exactSearchable ? "是" : "否" }}</template>
            </el-table-column>
            <el-table-column prop="fullTextSearchable" label="全文" width="80">
              <template #default="{ row }">{{ row.fullTextSearchable ? "是" : "否" }}</template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? "启用" : "停用" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditField(row)">编辑</el-button>
                <el-button link type="danger" @click="removeField(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="布局配置" name="layout">
          <div class="archive-layout-config">
            <div class="archive-layout-config__toolbar">
              <el-segmented v-model="activeLayoutSurface" :options="layoutSurfaceOptions" />
              <el-segmented v-model="activeLayoutScope" :options="layoutScopeOptions" />
              <el-button type="primary" :loading="saving" @click="saveLayout">保存布局</el-button>
            </div>
            <el-empty
              v-if="layoutItems.length === 0 && !layoutLoading"
              description="当前分类没有字段"
            />
            <VueDraggable
              v-else
              v-model="layoutItems"
              v-loading="layoutLoading || saving"
              class="archive-layout-config__list"
              :class="{ 'is-table': activeLayoutSurface === 'TABLE' }"
              handle=".archive-layout-config__drag"
              :animation="120"
              ghost-class="archive-layout-config__ghost"
            >
              <div
                v-for="item in layoutItems"
                :key="item.fieldId"
                class="archive-layout-config__item"
                :class="{ 'is-hidden': !item.visible }"
                :style="layoutItemStyle(item)"
              >
                <el-icon class="archive-layout-config__drag"><Rank /></el-icon>
                <div class="archive-layout-config__field">
                  <span class="archive-layout-config__name">{{ item.fieldName }}</span>
                  <span class="archive-layout-config__code">
                    {{ item.fieldCode }} · {{ layoutControlText(item) }}
                  </span>
                </div>
                <el-switch v-model="item.visible" />
                <el-input-number
                  v-if="activeLayoutSurface === 'TABLE'"
                  v-model="item.listWidth"
                  :min="80"
                  :max="600"
                  :step="10"
                  controls-position="right"
                  placeholder="列宽"
                />
                <el-radio-group v-else v-model="item.colSpan">
                  <el-radio-button :value="1">1列</el-radio-button>
                  <el-radio-button :value="2">2列</el-radio-button>
                </el-radio-group>
              </div>
            </VueDraggable>
          </div>
        </el-tab-pane>
        <el-tab-pane label="唯一约束" name="rules">
          <el-table v-loading="constraintsLoading" :data="currentLevelConstraints" height="100%">
            <el-table-column prop="archiveLevel" label="层级" width="80">
              <template #default="{ row }">{{
                row.archiveLevel === "VOLUME" ? "案卷" : "卷内"
              }}</template>
            </el-table-column>
            <el-table-column prop="constraintCode" label="约束编码" width="140" />
            <el-table-column prop="constraintName" label="约束名称" min-width="150" />
            <el-table-column label="字段组合" min-width="180">
              <template #default="{ row }">{{ constraintFieldNames(row) }}</template>
            </el-table-column>
            <el-table-column prop="includeFonds" label="全宗" width="80">
              <template #default="{ row }">{{ row.includeFonds ? "是" : "否" }}</template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? "启用" : "停用" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditConstraint(row)">编辑</el-button>
                <el-button link type="danger" @click="removeConstraint(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog
      v-model="categoryDialogVisible"
      :title="editingCategoryId ? '编辑分类' : '新增分类'"
      width="540px"
    >
      <el-form :model="categoryForm" label-width="92px">
        <el-form-item label="分类编码" required>
          <el-input v-model="categoryForm.categoryCode" />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="categoryForm.categoryName" />
        </el-form-item>
        <el-form-item label="上级分类">
          <el-tree-select
            v-model="categoryForm.parentId"
            :data="parentOptions"
            clearable
            check-strictly
            default-expand-all
            placeholder="无上级分类"
          />
        </el-form-item>
        <el-form-item label="管理模式" required>
          <el-select v-model="categoryForm.managementMode">
            <el-option
              v-for="item in managementModeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="categoryForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="fieldDialogVisible"
      :title="editingFieldId ? '编辑字段' : '新增字段'"
      width="560px"
    >
      <el-form :model="fieldForm" label-width="104px">
        <el-form-item label="适用层级" required>
          <el-segmented v-model="fieldForm.archiveLevel" :options="archiveLevelOptions" />
        </el-form-item>
        <el-form-item label="字段编码" required>
          <el-input v-model="fieldForm.fieldCode" />
        </el-form-item>
        <el-form-item label="字段名称" required>
          <el-input v-model="fieldForm.fieldName" />
        </el-form-item>
        <el-form-item label="字段类型" required>
          <el-select v-model="fieldForm.fieldType">
            <el-option
              v-for="item in fieldTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fieldForm.fieldType === 'TEXT'" label="文本长度">
          <el-input-number v-model="fieldForm.textLength" :min="1" />
        </el-form-item>
        <template v-if="fieldForm.fieldType === 'DECIMAL'">
          <el-form-item label="总位数">
            <el-input-number v-model="fieldForm.decimalPrecision" :min="1" />
          </el-form-item>
          <el-form-item label="小数位数">
            <el-input-number v-model="fieldForm.decimalScale" :min="0" />
          </el-form-item>
        </template>
        <el-form-item label="编辑控件">
          <el-select v-model="fieldForm.editControl">
            <el-option
              v-for="item in fieldControlOptions(fieldForm.fieldType)"
              :key="item"
              :label="fieldControlText(item)"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="精确搜索">
          <el-switch v-model="fieldForm.exactSearchable" />
        </el-form-item>
        <el-form-item label="全文检索">
          <el-switch v-model="fieldForm.fullTextSearchable" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="fieldForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="fieldForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fieldDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitField">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="constraintDialogVisible"
      :title="editingConstraintId ? '编辑唯一约束' : '新增唯一约束'"
      width="560px"
    >
      <el-form :model="constraintForm" label-width="104px">
        <el-form-item label="适用层级" required>
          <el-segmented v-model="constraintForm.archiveLevel" :options="archiveLevelOptions" />
        </el-form-item>
        <el-form-item label="约束编码" required>
          <el-input v-model="constraintForm.constraintCode" />
        </el-form-item>
        <el-form-item label="约束名称" required>
          <el-input v-model="constraintForm.constraintName" />
        </el-form-item>
        <el-form-item label="约束字段" required>
          <el-select v-model="constraintForm.fieldIds" multiple>
            <el-option
              v-for="field in fields.filter(
                (item) => item.archiveLevel === constraintForm.archiveLevel,
              )"
              :key="field.id"
              :label="field.fieldName"
              :value="field.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="包含全宗">
          <el-switch v-model="constraintForm.includeFonds" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="constraintForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="constraintDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitConstraint">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.archive-categories-page {
  display: grid;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  gap: 16px;
  grid-template-columns: minmax(420px, 0.9fr) minmax(520px, 1.1fr);
  padding: 20px;
}

.archive-categories-page__categories,
.archive-categories-page__fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--am-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--am-bg-surface);
}

.archive-categories-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.archive-categories-page__selected {
  min-width: 0;
  overflow: hidden;
  color: var(--am-text);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archive-categories-page__actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.archive-categories-page__tabs {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

:deep(.archive-categories-page__tabs > .el-tabs__content) {
  flex: 1;
  min-height: 0;
}

:deep(.archive-categories-page__tabs > .el-tabs__content > .el-tab-pane) {
  height: 100%;
  min-height: 0;
}

.archive-layout-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.archive-layout-config__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.archive-layout-config__list {
  display: grid;
  max-height: 420px;
  gap: 8px;
  align-content: start;
  overflow: auto;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.archive-layout-config__list.is-table {
  display: flex;
  align-items: stretch;
  overflow-x: auto;
}

.archive-layout-config__item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  border: 1px solid var(--am-border);
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--am-bg-surface);
}

.archive-layout-config__list.is-table .archive-layout-config__item {
  min-width: 180px;
}

.archive-layout-config__item.is-hidden {
  opacity: 0.56;
}

.archive-layout-config__ghost {
  opacity: 0.42;
}

.archive-layout-config__drag {
  flex: none;
  color: var(--am-text-muted);
  cursor: grab;
}

.archive-layout-config__field {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.archive-layout-config__name,
.archive-layout-config__code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archive-layout-config__name {
  color: var(--am-text);
  font-weight: 500;
}

.archive-layout-config__code {
  color: var(--am-text-muted);
  font-size: 12px;
}

.archive-layout-config__item :deep(.el-input-number) {
  width: 132px;
}
</style>
