<script setup lang="ts">
import { ElPopover, ElTooltip } from "element-plus";
import {
  computed,
  defineComponent,
  h,
  nextTick,
  onBeforeUnmount,
  onMounted,
  onUpdated,
  ref,
} from "vue";
import { Rank } from "@element-plus/icons-vue";
import { storeReactivityBindings } from "@tanstack/table-core/store-reactivity-bindings";
import { useVirtualizer } from "@tanstack/vue-virtual";
import {
  FlexRender,
  createColumnHelper,
  createCoreRowModel,
  createFilteredRowModel,
  createSortedRowModel,
  filterFns,
  sortFns,
  stockFeatures,
  tableFeatures,
  useTable,
  type Column,
  type ColumnOrderState,
  type ColumnPinningPosition,
  type ColumnPinningState,
  type columnResizingState,
  type ColumnSizingState,
  type Header,
  type SortingState,
} from "@tanstack/vue-table";

defineOptions({ name: "TanStackTableDemoPage" });

type ArchiveRow = {
  id: string;
  code: string;
  title: string;
  description: string;
  category: string;
  fonds: string;
  owner: string;
  year: number;
  retention: string;
  pages: number;
  status: "待整理" | "校验中" | "可入库" | "需复核";
};

type DemoDataSize = 12 | 5000;
type EditableField = "title" | "description" | "owner" | "pages" | "status";
type TableDensity = "small" | "medium" | "large";
type EditingCell = {
  draftValue: string;
  field: EditableField;
  rowId: string;
};
type SelectionMode = "partial" | "allFiltered";

const vFocusEditor = {
  mounted(element: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement): void {
    requestAnimationFrame(() => {
      element.focus();
    });
  },
};

const OverflowTooltipText = defineComponent({
  name: "OverflowTooltipText",
  props: {
    text: {
      required: true,
      type: String,
    },
  },
  setup(props) {
    const textRef = ref<HTMLElement | null>(null);
    const overflow = ref(false);
    let resizeObserver: ResizeObserver | null = null;

    function updateOverflow(): void {
      const element = textRef.value;
      overflow.value = element ? element.scrollWidth > element.clientWidth : false;
    }

    onMounted(() => {
      void nextTick(updateOverflow);
      const element = textRef.value;
      if (element && typeof ResizeObserver !== "undefined") {
        resizeObserver = new ResizeObserver(updateOverflow);
        resizeObserver.observe(element);
      }
    });
    onUpdated(() => {
      void nextTick(updateOverflow);
    });
    onBeforeUnmount(() => {
      resizeObserver?.disconnect();
      resizeObserver = null;
    });

    return () =>
      h(
        ElTooltip,
        {
          content: props.text,
          disabled: !overflow.value,
          placement: "top",
          showAfter: 100,
        },
        {
          default: () =>
            h(
              "span",
              {
                ref: textRef,
                class: "am-table__clip",
              },
              props.text,
            ),
        },
      );
  },
});

const tableFeatureSet = tableFeatures({
  coreReactivityFeature: storeReactivityBindings(),
  ...stockFeatures,
});
const columnHelper = createColumnHelper<typeof tableFeatureSet, ArchiveRow>();

const baseRows: ArchiveRow[] = [
  {
    id: "A-1001",
    code: "KJ-PZ-2026-001",
    title: "一月会计凭证",
    description: "包含记账凭证、原始凭证、附件清单及凭证装订检查记录。",
    category: "会计档案",
    fonds: "集团总部",
    owner: "财务部",
    year: 2026,
    retention: "30 年",
    pages: 184,
    status: "可入库",
  },
  {
    id: "A-1002",
    code: "KJ-ZB-2026-014",
    title: "总账明细",
    description: "按月归集的总账、明细账和科目余额表，需要核对年度、月份、会计期间和凭证来源。",
    category: "会计档案",
    fonds: "集团总部",
    owner: "财务部",
    year: 2026,
    retention: "30 年",
    pages: 96,
    status: "校验中",
  },
  {
    id: "A-1003",
    code: "HT-CG-2026-038",
    title: "服务器采购合同",
    description:
      "采购合同包含招采过程材料、合同正文、补充协议、验收记录和付款节点说明，题名较长时应在单元格内省略并通过悬停查看完整内容。",
    category: "合同档案",
    fonds: "信息中心",
    owner: "采购部",
    year: 2026,
    retention: "长期",
    pages: 42,
    status: "需复核",
  },
  {
    id: "A-1004",
    code: "WS-HY-2026-021",
    title: "档案工作会议纪要",
    description: "会议纪要、签到表、议题材料和后续落实事项汇总。",
    category: "文书档案",
    fonds: "档案室",
    owner: "办公室",
    year: 2026,
    retention: "永久",
    pages: 18,
    status: "待整理",
  },
  {
    id: "A-1005",
    code: "DZ-YX-2026-112",
    title: "项目验收影像",
    description:
      "电子影像材料数量较多，包含现场照片、验收视频、扫描件校验清单和文件完整性校验结果。",
    category: "电子档案",
    fonds: "工程中心",
    owner: "项目部",
    year: 2026,
    retention: "永久",
    pages: 320,
    status: "可入库",
  },
  {
    id: "A-1006",
    code: "HT-RS-2025-077",
    title: "培训服务合同",
    description: "培训服务合同正文、供应商资质、课程安排、验收单和付款审批材料。",
    category: "合同档案",
    fonds: "人力资源部",
    owner: "人力资源部",
    year: 2025,
    retention: "长期",
    pages: 36,
    status: "可入库",
  },
  {
    id: "A-1007",
    code: "WS-ZD-2025-063",
    title: "制度修订记录",
    description: "制度修订过程稿、征求意见、审批记录、发布通知和版本差异说明。",
    category: "文书档案",
    fonds: "集团总部",
    owner: "办公室",
    year: 2025,
    retention: "永久",
    pages: 55,
    status: "需复核",
  },
  {
    id: "A-1008",
    code: "KJ-BB-2025-201",
    title: "年度财务报表",
    description: "年度资产负债表、利润表、现金流量表、附注、审计调整记录及报表报送确认材料。",
    category: "会计档案",
    fonds: "集团总部",
    owner: "财务部",
    year: 2025,
    retention: "永久",
    pages: 128,
    status: "校验中",
  },
  {
    id: "A-1009",
    code: "DZ-YJ-2025-046",
    title: "邮件归档包",
    description: "按业务主题归集的邮件归档包，包含邮件正文、附件、收发人和归档校验摘要。",
    category: "电子档案",
    fonds: "信息中心",
    owner: "信息中心",
    year: 2025,
    retention: "10 年",
    pages: 76,
    status: "待整理",
  },
  {
    id: "A-1010",
    code: "HT-FW-2024-018",
    title: "物业服务合同",
    description: "物业服务合同、服务范围说明、年度考核记录、结算确认单和补充协议。",
    category: "合同档案",
    fonds: "后勤中心",
    owner: "后勤中心",
    year: 2024,
    retention: "长期",
    pages: 64,
    status: "可入库",
  },
  {
    id: "A-1011",
    code: "WS-TZ-2024-092",
    title: "项目调整通知",
    description:
      "项目调整通知涉及多部门流转，包含通知正文、变更依据、审批意见、执行回执和后续跟踪记录。",
    category: "文书档案",
    fonds: "工程中心",
    owner: "项目部",
    year: 2024,
    retention: "30 年",
    pages: 23,
    status: "需复核",
  },
  {
    id: "A-1012",
    code: "DZ-SM-2024-005",
    title: "扫描件抽检记录",
    description: "扫描件抽检样本、图像质量检查结果、重扫说明和抽检人员签名记录。",
    category: "电子档案",
    fonds: "档案室",
    owner: "档案室",
    year: 2024,
    retention: "30 年",
    pages: 210,
    status: "校验中",
  },
];

const longDescriptions = [
  "这是一条用于验证长文本单元格的说明：内容包含来源系统、移交批次、业务年度、责任部门、归档范围和后续处理建议，正常展示时只显示一行，悬停后通过 Tooltip 查看完整文本。",
  "导入或批量生成的数据中经常会出现很长的题名、备注或错误原因，表格应保持行高稳定，不能因为文本过长把整行撑开。",
  "该记录包含跨部门协同材料、审批附件、扫描件、电子原文和校验摘要，后续可扩展为错误回填和在线修正场景。",
];

const statusOptions: ArchiveRow["status"][] = ["待整理", "校验中", "可入库", "需复核"];

function makeRows(count: DemoDataSize): ArchiveRow[] {
  if (count === 12) {
    return baseRows;
  }

  return Array.from({ length: count }, (_, index) => {
    const source = baseRows[index % baseRows.length];
    const sequence = index + 1;
    const year = 2026 - (index % 4);

    return {
      ...source,
      id: `A-${String(2000 + sequence).padStart(4, "0")}`,
      code: `${source.code.slice(0, source.code.lastIndexOf("-"))}-${String(sequence).padStart(4, "0")}`,
      title:
        index % 9 === 0
          ? `${source.title}（跨部门移交批次 ${sequence}，包含多个附件和补充说明）`
          : `${source.title} ${sequence}`,
      description:
        index % 5 === 0
          ? longDescriptions[index % longDescriptions.length]
          : `${source.description} 批次序号：${sequence}。`,
      year,
      pages: source.pages + (index % 37),
      status: statusOptions[index % statusOptions.length],
    };
  });
}

const dataSize = ref<DemoDataSize>(5000);
const rows = ref<ArchiveRow[]>(makeRows(dataSize.value));
const tableBodyRef = ref<HTMLDivElement | null>(null);

const tableDensity = ref<TableDensity>("medium");
const globalFilter = ref("");
const selectionMode = ref<SelectionMode>("partial");
const selectedRowIds = ref<Set<string>>(new Set());
const excludedRowIds = ref<Set<string>>(new Set());
const sorting = ref<SortingState>([]);
const defaultColumnPinning: ColumnPinningState = {
  left: ["select", "code"],
  right: [],
};
const columnOrder = ref<ColumnOrderState>([
  "select",
  "code",
  "title",
  "description",
  "category",
  "fonds",
  "owner",
  "year",
  "retention",
  "pages",
  "status",
]);
const columnSizing = ref<ColumnSizingState>({});
const columnResizing = ref<columnResizingState>({
  columnSizingStart: [],
  deltaOffset: null,
  deltaPercentage: null,
  isResizingColumn: false,
  startOffset: null,
  startSize: null,
});
const columnPinning = ref<ColumnPinningState>({
  left: [...defaultColumnPinning.left],
  right: [...defaultColumnPinning.right],
});
const columnVisibility = ref<Record<string, boolean>>({});
const editedCellKeys = ref<Set<string>>(new Set());
const editingCell = ref<EditingCell | null>(null);
const draggedColumnId = ref<string | null>(null);

const columns = columnHelper.columns([
  columnHelper.display({
    id: "select",
    header: "选择",
    size: 72,
    minSize: 64,
    enableSorting: false,
    enableColumnFilter: false,
    enableResizing: false,
    enableHiding: false,
  }),
  columnHelper.accessor("code", {
    id: "code",
    header: "档号",
    size: 172,
    minSize: 140,
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("title", {
    id: "title",
    header: "题名",
    size: 220,
    minSize: 180,
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("description", {
    id: "description",
    header: "说明",
    size: 280,
    minSize: 180,
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("category", {
    id: "category",
    header: "分类",
    size: 130,
    minSize: 110,
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("fonds", {
    id: "fonds",
    header: "全宗",
    size: 128,
    minSize: 110,
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("owner", {
    id: "owner",
    header: "责任部门",
    size: 136,
    minSize: 120,
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("year", {
    id: "year",
    header: "年度",
    size: 104,
    minSize: 88,
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("retention", {
    id: "retention",
    header: "保管期限",
    size: 120,
    minSize: 104,
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("pages", {
    id: "pages",
    header: "页数",
    size: 104,
    minSize: 88,
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("status", {
    id: "status",
    header: "状态",
    size: 124,
    minSize: 112,
    cell: (info) => info.getValue(),
  }),
]);

const table = useTable(
  {
    features: tableFeatureSet,
    rowModels: {
      coreRowModel: createCoreRowModel(),
      filteredRowModel: createFilteredRowModel(filterFns),
      sortedRowModel: createSortedRowModel(sortFns),
    },
    columns,
    data: rows,
    getRowId: (row) => row.id,
    enableColumnResizing: true,
    enableGlobalFilter: true,
    columnResizeMode: "onChange",
    getColumnCanGlobalFilter: (column) => ["code", "title", "description"].includes(column.id),
    state: {
      get columnFilters() {
        return [];
      },
      get columnResizing() {
        return columnResizing.value;
      },
      get columnOrder() {
        return columnOrder.value;
      },
      get columnPinning() {
        return columnPinning.value;
      },
      get columnSizing() {
        return columnSizing.value;
      },
      get columnVisibility() {
        return columnVisibility.value;
      },
      get globalFilter() {
        return globalFilter.value;
      },
      get sorting() {
        return sorting.value;
      },
    },
    onGlobalFilterChange: (updater) => {
      globalFilter.value = typeof updater === "function" ? updater(globalFilter.value) : updater;
    },
    onColumnOrderChange: (updater) => {
      columnOrder.value = typeof updater === "function" ? updater(columnOrder.value) : updater;
    },
    onColumnPinningChange: (updater) => {
      columnPinning.value = typeof updater === "function" ? updater(columnPinning.value) : updater;
    },
    onColumnResizingChange: (updater) => {
      columnResizing.value =
        typeof updater === "function" ? updater(columnResizing.value) : updater;
    },
    onColumnSizingChange: (updater) => {
      columnSizing.value = typeof updater === "function" ? updater(columnSizing.value) : updater;
    },
    onColumnVisibilityChange: (updater) => {
      columnVisibility.value =
        typeof updater === "function" ? updater(columnVisibility.value) : updater;
    },
    onSortingChange: (updater) => {
      sorting.value = typeof updater === "function" ? updater(sorting.value) : updater;
    },
  },
  (state) => ({
    columnOrder: state.columnOrder,
    columnPinning: state.columnPinning,
    columnResizing: state.columnResizing,
    columnSizing: state.columnSizing,
    columnVisibility: state.columnVisibility,
    sorting: state.sorting,
  }),
);

const editableFields = new Set<string>(["title", "description", "owner", "pages", "status"]);
const tooltipFields = new Set<string>(["title", "description"]);
const densityOptions: Array<{ label: string; value: TableDensity }> = [
  { label: "Small", value: "small" },
  { label: "Medium", value: "medium" },
  { label: "Large", value: "large" },
];
const tableDensityConfigs: Record<
  TableDensity,
  {
    cellPadding: string;
    editorHeight: number;
    fontSize: number;
    headerHeight: number;
    rowHeight: number;
  }
> = {
  small: {
    cellPadding: "5px 8px",
    editorHeight: 26,
    fontSize: 13,
    headerHeight: 38,
    rowHeight: 40,
  },
  medium: {
    cellPadding: "8px 10px",
    editorHeight: 30,
    fontSize: 14,
    headerHeight: 44,
    rowHeight: 48,
  },
  large: {
    cellPadding: "10px 12px",
    editorHeight: 34,
    fontSize: 15,
    headerHeight: 50,
    rowHeight: 56,
  },
};
const tableDensityConfig = computed(() => tableDensityConfigs[tableDensity.value]);
const tableDensityStyle = computed(() => ({
  "--am-table-cell-padding": tableDensityConfig.value.cellPadding,
  "--am-table-editor-height": `${tableDensityConfig.value.editorHeight}px`,
  "--am-table-font-size": `${tableDensityConfig.value.fontSize}px`,
  "--am-table-header-height": `${tableDensityConfig.value.headerHeight}px`,
  "--am-table-row-height": `${tableDensityConfig.value.rowHeight}px`,
}));

const tableRows = computed(() => table.getRowModel().rows);
const visibleCount = computed(() => tableRows.value.length);
const filteredRows = computed(() => tableRows.value);
const filteredRowIds = computed(() => new Set(filteredRows.value.map((row) => row.id)));
const selectedCount = computed(() => {
  if (selectionMode.value === "allFiltered") {
    let excludedVisibleCount = 0;
    for (const rowId of excludedRowIds.value) {
      if (filteredRowIds.value.has(rowId)) {
        excludedVisibleCount += 1;
      }
    }
    return Math.max(visibleCount.value - excludedVisibleCount, 0);
  }

  let selectedVisibleCount = 0;
  for (const rowId of selectedRowIds.value) {
    if (filteredRowIds.value.has(rowId)) {
      selectedVisibleCount += 1;
    }
  }
  return selectedVisibleCount;
});
const allFilteredRowsSelected = computed(
  () => visibleCount.value > 0 && selectedCount.value === visibleCount.value,
);
const someFilteredRowsSelected = computed(
  () => selectedCount.value > 0 && selectedCount.value < visibleCount.value,
);

const rowVirtualizer = useVirtualizer(
  computed(() => ({
    count: tableRows.value.length,
    estimateSize: () => tableDensityConfig.value.rowHeight,
    getItemKey: (index) => tableRows.value[index]?.id ?? index,
    getScrollElement: () => tableBodyRef.value,
    overscan: 12,
  })),
);

const virtualRows = computed(() =>
  rowVirtualizer.value.getVirtualItems().flatMap((virtualRow) => {
    const row = tableRows.value[virtualRow.index];
    return row ? [{ row, virtualRow }] : [];
  }),
);

function switchTableDensity(density: TableDensity): void {
  tableDensity.value = density;
  editingCell.value = null;
  void nextTick(() => {
    rowVirtualizer.value.measure();
  });
}

function afterTableModelChange(): void {
  editingCell.value = null;
  void nextTick(() => {
    tableBodyRef.value?.scrollTo({ top: 0 });
    rowVirtualizer.value.scrollToIndex(0);
  });
}

function afterGlobalFilterInput(): void {
  afterTableModelChange();
}

function isEditableField(columnId: string): columnId is EditableField {
  return editableFields.has(columnId);
}

function useOverflowTooltip(columnId: string): boolean {
  return tooltipFields.has(columnId);
}

function columnWidthStyle(column: Column<typeof tableFeatureSet, ArchiveRow, unknown>): string {
  void columnSizing.value;
  return `${column.getSize()}px`;
}

function columnPinningStyle(
  column: Column<typeof tableFeatureSet, ArchiveRow, unknown>,
  isHeader: boolean,
): Record<string, string | number> {
  void columnPinning.value;
  void columnSizing.value;

  const pinned = column.getIsPinned();
  const style: Record<string, string | number> = {
    width: columnWidthStyle(column),
  };

  if (!pinned) {
    return style;
  }

  style.position = "sticky";
  style.zIndex = isHeader ? 8 : 5;
  if (pinned === "left") {
    style.left = `${column.getStart("left")}px`;
  } else {
    style.right = `${column.getAfter("right")}px`;
  }

  return style;
}

function columnPinningClass(column: Column<typeof tableFeatureSet, ArchiveRow, unknown>) {
  const pinned = column.getIsPinned();
  return {
    "is-pinned-left": pinned === "left",
    "is-pinned-right": pinned === "right",
  };
}

function pinColumn(
  column: Column<typeof tableFeatureSet, ArchiveRow, unknown>,
  position: ColumnPinningPosition,
): void {
  if (!column.getCanPin()) {
    return;
  }
  column.pin(position);
  editingCell.value = null;
}

function isEditingCell(rowId: string, columnId: string): columnId is EditableField {
  return (
    editingCell.value?.rowId === rowId &&
    editingCell.value.field === columnId &&
    isEditableField(columnId)
  );
}

function startEditingCell(row: ArchiveRow, field: EditableField): void {
  editingCell.value = {
    draftValue: String(row[field] ?? ""),
    field,
    rowId: row.id,
  };
}

function cancelEditingCell(): void {
  editingCell.value = null;
}

function updateEditingDraft(value: string): void {
  const currentEditingCell = editingCell.value;
  if (!currentEditingCell) {
    return;
  }
  editingCell.value = {
    ...currentEditingCell,
    draftValue: value,
  };
}

function commitEditingCell(): void {
  const currentEditingCell = editingCell.value;
  if (!currentEditingCell) {
    return;
  }

  updateCell(currentEditingCell.rowId, currentEditingCell.field, currentEditingCell.draftValue);
}

function finishEditingCell(): void {
  commitEditingCell();
}

function updateCell(rowId: string, field: EditableField, value: string): void {
  let changed = false;

  rows.value = rows.value.map((row) => {
    if (row.id !== rowId) {
      return row;
    }
    const nextValue = field === "pages" ? Number(value) : value;
    if (field === "pages" && Number.isNaN(nextValue)) {
      return row;
    }
    if (row[field] === nextValue) {
      return row;
    }
    changed = true;
    return {
      ...row,
      [field]: nextValue,
    };
  });

  if (changed) {
    const nextKeys = new Set(editedCellKeys.value);
    nextKeys.add(`${rowId}:${field}`);
    editedCellKeys.value = nextKeys;
  }
  editingCell.value = null;
}

function cellEdited(rowId: string, field: string): boolean {
  return editedCellKeys.value.has(`${rowId}:${field}`);
}

function clearSelection(): void {
  selectionMode.value = "partial";
  selectedRowIds.value = new Set();
  excludedRowIds.value = new Set();
}

function isRowSelected(rowId: string): boolean {
  if (selectionMode.value === "allFiltered") {
    return filteredRowIds.value.has(rowId) && !excludedRowIds.value.has(rowId);
  }
  return selectedRowIds.value.has(rowId);
}

function toggleAllFilteredRows(checked: boolean): void {
  if (!checked) {
    clearSelection();
    return;
  }

  selectionMode.value = "allFiltered";
  selectedRowIds.value = new Set();
  excludedRowIds.value = new Set();
}

function toggleRowSelected(rowId: string, checked: boolean): void {
  if (selectionMode.value === "allFiltered") {
    const nextExcludedRowIds = new Set(excludedRowIds.value);
    if (checked) {
      nextExcludedRowIds.delete(rowId);
    } else {
      nextExcludedRowIds.add(rowId);
    }
    excludedRowIds.value = nextExcludedRowIds;
    return;
  }

  const nextSelectedRowIds = new Set(selectedRowIds.value);
  if (checked) {
    nextSelectedRowIds.add(rowId);
  } else {
    nextSelectedRowIds.delete(rowId);
  }
  selectedRowIds.value = nextSelectedRowIds;
}

function resetTableState(): void {
  globalFilter.value = "";
  sorting.value = [];
  clearSelection();
  columnVisibility.value = {};
  columnOrder.value = columns.map((column) => column.id ?? "");
  columnPinning.value = {
    left: [...defaultColumnPinning.left],
    right: [...defaultColumnPinning.right],
  };
  columnResizing.value = {
    columnSizingStart: [],
    deltaOffset: null,
    deltaPercentage: null,
    isResizingColumn: false,
    startOffset: null,
    startSize: null,
  };
  columnSizing.value = {};
  editedCellKeys.value = new Set();
  editingCell.value = null;
}

function switchDataSize(size: DemoDataSize): void {
  dataSize.value = size;
  rows.value = makeRows(size);
  clearSelection();
  editedCellKeys.value = new Set();
  editingCell.value = null;
  tableBodyRef.value?.scrollTo({ top: 0 });
}

function startColumnResize(
  header: Header<typeof tableFeatureSet, ArchiveRow, unknown>,
  event: MouseEvent | TouchEvent,
): void {
  event.preventDefault();
  event.stopPropagation();
  header.getResizeHandler(document)(event);
}

function onDragStart(columnId: string): void {
  if (columnId === "select") {
    return;
  }
  draggedColumnId.value = columnId;
}

function onDrop(targetColumnId: string): void {
  const sourceColumnId = draggedColumnId.value;
  draggedColumnId.value = null;

  if (!sourceColumnId || sourceColumnId === targetColumnId || targetColumnId === "select") {
    return;
  }

  const nextOrder = [...columnOrder.value];
  const sourceIndex = nextOrder.indexOf(sourceColumnId);
  const targetIndex = nextOrder.indexOf(targetColumnId);

  if (sourceIndex < 0 || targetIndex < 0) {
    return;
  }

  nextOrder.splice(sourceIndex, 1);
  nextOrder.splice(targetIndex, 0, sourceColumnId);
  columnOrder.value = nextOrder;
}

function columnLabel(column: Column<typeof tableFeatureSet, ArchiveRow, unknown>): string {
  const header = column.columnDef.header;
  return typeof header === "string" ? header : column.id;
}
</script>

<template>
  <section class="table-demo-page">
    <section class="table-demo-page__toolbar">
      <el-input
        v-model="globalFilter"
        class="table-demo-page__search"
        clearable
        placeholder="筛选档号、题名或说明"
        @input="afterGlobalFilterInput"
      />
      <el-button-group>
        <el-button :type="dataSize === 5000 ? 'primary' : 'default'" @click="switchDataSize(5000)">
          5000 行
        </el-button>
        <el-button :type="dataSize === 12 ? 'primary' : 'default'" @click="switchDataSize(12)">
          12 行
        </el-button>
      </el-button-group>
      <el-button-group>
        <el-button
          v-for="option in densityOptions"
          :key="option.value"
          :type="tableDensity === option.value ? 'primary' : 'default'"
          @click="switchTableDensity(option.value)"
        >
          {{ option.label }}
        </el-button>
      </el-button-group>
      <el-dropdown trigger="click" max-height="320">
        <el-button>列显示</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="column in table.getAllLeafColumns()" :key="column.id">
              <div class="table-demo-page__column-menu-item">
                <el-checkbox
                  :model-value="column.getIsVisible()"
                  :disabled="!column.getCanHide()"
                  @change="(checked) => column.toggleVisibility(Boolean(checked))"
                >
                  {{ columnLabel(column) }}
                </el-checkbox>
                <el-button-group v-if="column.getCanPin()" size="small">
                  <el-button
                    :type="column.getIsPinned() === 'left' ? 'primary' : 'default'"
                    @click.stop="pinColumn(column, 'left')"
                  >
                    左
                  </el-button>
                  <el-button
                    :type="column.getIsPinned() === 'right' ? 'primary' : 'default'"
                    @click.stop="pinColumn(column, 'right')"
                  >
                    右
                  </el-button>
                  <el-button
                    :disabled="!column.getIsPinned()"
                    @click.stop="pinColumn(column, false)"
                  >
                    取消
                  </el-button>
                </el-button-group>
              </div>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-tag type="info" effect="plain">筛选后 {{ visibleCount }} 条</el-tag>
      <el-tag type="info" effect="plain">当前渲染 {{ virtualRows.length }} 行</el-tag>
      <el-tag type="success" effect="plain">已选 {{ selectedCount }} 条</el-tag>
      <el-tag v-if="editedCellKeys.size > 0" type="warning" effect="plain">
        已编辑 {{ editedCellKeys.size }} 个单元格
      </el-tag>
      <el-button @click="resetTableState">重置演示状态</el-button>
    </section>

    <section ref="tableBodyRef" class="table-demo-page__body">
      <div
        class="am-table"
        role="table"
        aria-label="TanStack 表格基础能力 Demo"
        :style="tableDensityStyle"
      >
        <div
          v-for="headerGroup in table.getHeaderGroups()"
          :key="headerGroup.id"
          class="am-table__header-row"
          role="row"
        >
          <div
            v-for="header in headerGroup.headers"
            :key="header.id"
            class="am-table__header-cell"
            :class="{
              'is-select': header.column.id === 'select',
              'is-dragging': draggedColumnId === header.column.id,
              'is-sorted': Boolean(header.column.getIsSorted()),
              ...columnPinningClass(header.column),
            }"
            role="columnheader"
            :style="columnPinningStyle(header.column, true)"
            @dragover.prevent
            @drop="onDrop(header.column.id)"
          >
            <template v-if="!header.isPlaceholder">
              <el-checkbox
                v-if="header.column.id === 'select'"
                :model-value="allFilteredRowsSelected"
                :indeterminate="someFilteredRowsSelected"
                aria-label="选择全部筛选结果"
                @change="(checked) => toggleAllFilteredRows(Boolean(checked))"
              />
              <button
                v-else
                class="am-table__drag-handle"
                type="button"
                draggable="true"
                aria-label="拖拽调整列顺序"
                @click.stop
                @dragstart.stop="onDragStart(header.column.id)"
              >
                <el-icon><Rank /></el-icon>
              </button>
              <button
                v-if="header.column.id !== 'select'"
                class="am-table__sort-button"
                type="button"
                @click="
                  header.column.toggleSorting(undefined, $event.shiftKey);
                  afterTableModelChange();
                "
              >
                <FlexRender :header="header" />
                <span class="am-table__sort-mark">
                  {{
                    header.column.getIsSorted() === "asc"
                      ? "升序"
                      : header.column.getIsSorted() === "desc"
                        ? "降序"
                        : "排序"
                  }}
                </span>
              </button>
            </template>
            <button
              v-if="header.column.getCanResize()"
              class="am-table__resize-handle"
              type="button"
              aria-label="调整列宽"
              draggable="false"
              @dragstart.prevent.stop
              @mousedown="startColumnResize(header, $event)"
              @touchstart="startColumnResize(header, $event)"
            />
          </div>
        </div>

        <div
          class="am-table__body"
          role="rowgroup"
          :style="{ height: `${rowVirtualizer.getTotalSize()}px` }"
        >
          <div
            v-for="{ row, virtualRow } in virtualRows"
            :key="row.id"
            class="am-table__row"
            :class="{ 'is-selected': isRowSelected(row.id) }"
            role="row"
            :style="{ transform: `translateY(${virtualRow.start}px)` }"
          >
            <div
              v-for="cell in row.getVisibleCells()"
              :key="cell.id"
              class="am-table__cell"
              :class="{
                'is-select': cell.column.id === 'select',
                'is-edited': cellEdited(row.original.id, cell.column.id),
                ...columnPinningClass(cell.column),
              }"
              role="cell"
              :style="columnPinningStyle(cell.column, false)"
            >
              <el-checkbox
                v-if="cell.column.id === 'select'"
                :model-value="isRowSelected(row.id)"
                aria-label="选择当前行"
                @change="(checked) => toggleRowSelected(row.id, Boolean(checked))"
              />
              <template v-else-if="isEditableField(cell.column.id)">
                <template v-if="isEditingCell(row.original.id, cell.column.id)">
                  <select
                    v-if="cell.column.id === 'status'"
                    v-focus-editor
                    class="am-table__cell-editor"
                    :value="editingCell?.draftValue ?? row.original.status"
                    @blur="finishEditingCell"
                    @keydown.esc.prevent="cancelEditingCell"
                    @change="
                      updateCell(
                        row.original.id,
                        'status',
                        ($event.target as HTMLSelectElement).value,
                      )
                    "
                  >
                    <option v-for="status in statusOptions" :key="status" :value="status">
                      {{ status }}
                    </option>
                  </select>
                  <input
                    v-else-if="cell.column.id === 'pages'"
                    v-focus-editor
                    class="am-table__cell-editor"
                    type="number"
                    min="1"
                    max="9999"
                    :value="editingCell?.draftValue ?? String(row.original.pages)"
                    @input="updateEditingDraft(($event.target as HTMLInputElement).value)"
                    @blur="
                      updateEditingDraft(($event.target as HTMLInputElement).value);
                      finishEditingCell();
                    "
                    @keydown.enter="($event.currentTarget as HTMLInputElement).blur()"
                    @keydown.esc.prevent="cancelEditingCell"
                  />
                  <ElPopover
                    v-else-if="cell.column.id === 'description'"
                    :visible="true"
                    :width="440"
                    placement="right-start"
                    popper-class="am-table__edit-popover"
                    trigger="manual"
                  >
                    <template #reference>
                      <button class="am-table__editable-text is-editing" type="button">
                        <OverflowTooltipText :text="row.original.description" />
                      </button>
                    </template>
                    <textarea
                      v-focus-editor
                      class="am-table__floating-editor"
                      :value="editingCell?.draftValue ?? row.original.description"
                      @blur="
                        updateEditingDraft(($event.target as HTMLTextAreaElement).value);
                        finishEditingCell();
                      "
                      @input="updateEditingDraft(($event.target as HTMLTextAreaElement).value)"
                      @keydown.esc.prevent="cancelEditingCell"
                    />
                  </ElPopover>
                  <input
                    v-else
                    v-focus-editor
                    class="am-table__cell-editor"
                    type="text"
                    :value="editingCell?.draftValue ?? String(cell.getValue() ?? '')"
                    @input="updateEditingDraft(($event.target as HTMLInputElement).value)"
                    @blur="
                      updateEditingDraft(($event.target as HTMLInputElement).value);
                      finishEditingCell();
                    "
                    @keydown.enter="($event.currentTarget as HTMLInputElement).blur()"
                    @keydown.esc.prevent="cancelEditingCell"
                  />
                </template>
                <button
                  v-else
                  class="am-table__editable-text"
                  type="button"
                  @click="startEditingCell(row.original, cell.column.id)"
                >
                  <OverflowTooltipText
                    v-if="useOverflowTooltip(cell.column.id)"
                    :text="String(cell.getValue() ?? '')"
                  />
                  <span v-else class="am-table__clip">{{ cell.getValue() }}</span>
                </button>
              </template>
              <OverflowTooltipText
                v-else-if="useOverflowTooltip(cell.column.id)"
                :text="String(cell.getValue() ?? '')"
              />
              <FlexRender v-else :cell="cell" />
            </div>
          </div>

          <div v-if="tableRows.length === 0" class="am-table__empty">没有符合筛选条件的数据</div>
        </div>
      </div>
    </section>

    <footer class="table-demo-page__footer">
      <div class="table-demo-page__page-info">
        数据集 {{ rows.length }} 条，当前筛选结果
        {{ tableRows.length }} 条，虚拟滚动只挂载可视区行。
      </div>
    </footer>
  </section>
</template>

<style scoped lang="scss">
.table-demo-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 20px;
}

.table-demo-page__toolbar,
.table-demo-page__footer {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--am-border);
  background: var(--am-bg-surface);
}

.table-demo-page__toolbar {
  flex-wrap: wrap;
  margin-bottom: 12px;
  border-radius: 8px;
  padding: 12px;
}

.table-demo-page__search {
  width: 280px;
}

.table-demo-page__column-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 260px;
}

.table-demo-page__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--am-border);
  border-radius: 8px;
  background: var(--am-bg-surface);
}

.table-demo-page__footer {
  justify-content: space-between;
  margin-top: 12px;
  border-radius: 8px;
  padding: 10px 12px;
}

.table-demo-page__page-info {
  color: var(--am-text-muted);
  font-size: 14px;
}

.am-table {
  min-width: max-content;
  color: var(--am-text);
  font-size: var(--am-table-font-size);
}

.am-table__header-row,
.am-table__row {
  display: flex;
  min-width: max-content;
}

.am-table__header-row {
  position: sticky;
  top: 0;
  z-index: 2;
  border-bottom: 1px solid var(--am-border);
  background: var(--am-bg-subtle);
  min-height: var(--am-table-header-height);
}

.am-table__row {
  position: absolute;
  top: 0;
  left: 0;
  box-sizing: border-box;
  height: var(--am-table-row-height);
  min-height: var(--am-table-row-height);
  border-bottom: 1px solid var(--am-border);
  background: var(--am-bg-surface);

  &:hover {
    background: #f8fbff;
  }

  &.is-selected {
    background: #eff6ff;
  }
}

.am-table__body {
  position: relative;
  min-width: max-content;
}

.am-table__header-cell,
.am-table__cell {
  position: relative;
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  box-sizing: border-box;
  min-width: 0;
  border-right: 1px solid var(--am-border);
  padding: var(--am-table-cell-padding);
}

.am-table__header-cell {
  gap: 6px;
  min-height: var(--am-table-header-height);
  font-weight: 600;
  user-select: none;

  &.is-dragging {
    background: #eaf2ff;
  }
}

.am-table__header-cell.is-pinned-left,
.am-table__header-cell.is-pinned-right {
  background: var(--am-bg-subtle);
}

.am-table__cell {
  height: var(--am-table-row-height);
  overflow: hidden;
  white-space: nowrap;

  &.is-edited::after {
    position: absolute;
    top: 6px;
    right: 6px;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #f59e0b;
    content: "";
  }
}

.am-table__cell.is-pinned-left,
.am-table__cell.is-pinned-right {
  background: inherit;
}

.am-table__header-cell.is-pinned-left,
.am-table__cell.is-pinned-left {
  box-shadow: 4px 0 4px -4px rgba(15, 23, 42, 0.28);
}

.am-table__header-cell.is-pinned-right,
.am-table__cell.is-pinned-right {
  box-shadow: -4px 0 4px -4px rgba(15, 23, 42, 0.28);
}

.am-table__cell-editor {
  display: block;
  width: 100%;
  min-width: 0;
  height: var(--am-table-editor-height);
  box-sizing: border-box;
  border: 1px solid var(--am-primary);
  border-radius: 4px;
  padding: 4px 8px;
  background: var(--am-bg-surface);
  color: inherit;
  font: inherit;
  line-height: calc(var(--am-table-editor-height) - 10px);
  outline: none;

  &:focus {
    border-color: var(--am-primary);
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.16);
  }
}

.am-table__floating-editor {
  display: block;
  width: 100%;
  height: 168px;
  box-sizing: border-box;
  border: 1px solid var(--am-primary);
  border-radius: 4px;
  padding: 8px 10px;
  background: var(--am-bg-surface);
  color: var(--am-text);
  font: inherit;
  line-height: 1.5;
  outline: none;
  resize: vertical;

  &:focus {
    border-color: var(--am-primary);
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.16);
  }
}

:global(.am-table__edit-popover) {
  padding: 8px;
}

.am-table__header-cell.is-select,
.am-table__cell.is-select {
  justify-content: center;
  padding-right: 8px;
  padding-left: 8px;
}

.am-table__drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 20px;
  height: 24px;
  border: 0;
  border-radius: 4px;
  padding: 0;
  background: transparent;
  color: var(--am-text-muted);
  cursor: grab;

  &:active {
    cursor: grabbing;
  }

  &:hover,
  &:focus-visible {
    background: #eef4ff;
    color: var(--am-primary);
    outline: none;
  }
}

.am-table__sort-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.am-table__sort-mark {
  margin-left: 8px;
  color: var(--am-text-muted);
  font-size: 12px;
  font-weight: 400;
}

.am-table__editable-text {
  display: block;
  width: 100%;
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  cursor: text;
  font: inherit;
  text-align: left;

  &:focus-visible {
    outline: 2px solid rgba(37, 99, 235, 0.36);
    outline-offset: 2px;
  }

  &.is-editing {
    outline: 2px solid rgba(37, 99, 235, 0.24);
    outline-offset: 2px;
  }
}

:global(.am-table__clip) {
  display: block;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: clip;
}

.am-table__resize-handle {
  position: absolute;
  top: 0;
  right: -4px;
  bottom: 0;
  z-index: 1;
  width: 8px;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: col-resize;

  &:hover,
  &:focus-visible {
    background: rgba(37, 99, 235, 0.16);
    outline: none;
  }
}

.am-table__empty {
  padding: 40px;
  color: var(--am-text-muted);
  text-align: center;
}
</style>
