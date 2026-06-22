export type ArchiveFieldType = "TEXT" | "INTEGER" | "DECIMAL" | "DATE" | "DATETIME";
export type ArchiveFieldControl = "INPUT" | "TEXTAREA" | "NUMBER" | "DATE" | "DATETIME";
export type ArchiveTableStatus = "NOT_BUILT" | "BUILT";
export type ArchiveLayoutSurface = "TABLE" | "DETAIL" | "EDIT";
export type ArchiveLayoutScope = "PUBLIC" | "MINE" | "EFFECTIVE";

export interface ArchiveFondsDto {
  id: number;
  fondsCode: string;
  fondsName: string;
  enabled: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface ArchiveFondsCommand {
  fondsCode: string;
  fondsName: string;
  enabled: boolean;
  sortOrder: number;
}

export interface ArchiveCategoryDto {
  id: number;
  parentId?: number;
  categoryCode: string;
  categoryName: string;
  recordTableName?: string;
  tableStatus: ArchiveTableStatus;
  builtAt?: string;
  enabled: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface ArchiveCategoryCommand {
  categoryCode: string;
  categoryName: string;
  parentId?: number;
  enabled: boolean;
  sortOrder: number;
}

export interface ArchiveFieldDto {
  id: number;
  categoryId: number;
  fieldCode: string;
  fieldName: string;
  fieldType: ArchiveFieldType;
  columnName: string;
  textLength?: number;
  decimalPrecision?: number;
  decimalScale?: number;
  editControl: ArchiveFieldControl;
  listVisible: boolean;
  listWidth?: number;
  listSortOrder: number;
  detailVisible: boolean;
  detailColSpan: number;
  detailSortOrder: number;
  editVisible: boolean;
  editColSpan: number;
  editSortOrder: number;
  exactSearchable: boolean;
  fullTextSearchable: boolean;
  enabled: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface ArchiveFieldCommand {
  fieldCode: string;
  fieldName: string;
  fieldType: ArchiveFieldType;
  textLength?: number;
  decimalPrecision?: number;
  decimalScale?: number;
  editControl?: ArchiveFieldControl;
  listVisible: boolean;
  listWidth?: number;
  listSortOrder: number;
  detailVisible: boolean;
  detailColSpan: number;
  detailSortOrder: number;
  editVisible: boolean;
  editColSpan: number;
  editSortOrder: number;
  exactSearchable: boolean;
  fullTextSearchable: boolean;
  enabled: boolean;
  sortOrder: number;
}

export interface ArchiveFieldLayoutDto {
  surface: ArchiveLayoutSurface;
  scope: ArchiveLayoutScope;
  items: ArchiveFieldLayoutItemDto[];
}

export interface ArchiveFieldLayoutItemDto {
  fieldId: number;
  fieldCode: string;
  fieldName: string;
  fieldType: ArchiveFieldType;
  editControl: ArchiveFieldControl;
  visible: boolean;
  listWidth?: number;
  colSpan: number;
  rowOrder: number;
  colOrder: number;
}

export interface ArchiveFieldLayoutCommand {
  items: ArchiveFieldLayoutItemCommand[];
}

export interface ArchiveFieldLayoutItemCommand {
  fieldId: number;
  visible: boolean;
  listWidth?: number;
  colSpan: number;
  rowOrder: number;
  colOrder: number;
}

export interface ArchiveUniqueRuleFieldDto {
  fieldId: number;
  fieldOrder: number;
  fieldCode: string;
  fieldName: string;
  columnName: string;
}

export interface ArchiveUniqueRuleDto {
  id: number;
  categoryId: number;
  ruleCode: string;
  ruleName: string;
  includeFonds: boolean;
  indexName: string;
  enabled: boolean;
  fields: ArchiveUniqueRuleFieldDto[];
  createdAt: string;
  updatedAt: string;
}

export interface ArchiveUniqueRuleCommand {
  ruleCode: string;
  ruleName: string;
  includeFonds: boolean;
  enabled: boolean;
  fieldIds: number[];
}

export interface ArchiveRecordQuery {
  categoryId?: number;
  fondsCode?: string;
  keyword?: string;
  exactFilters?: Record<string, unknown>;
  filters?: ArchiveRecordFieldFilter[];
}

export interface ArchiveRecordFieldFilter {
  fieldCode: string;
  value?: unknown;
  startValue?: unknown;
  endValue?: unknown;
}

export interface ArchiveRecordCommand {
  categoryId: number;
  fondsCode: string;
  archiveNo?: string;
  archiveYear?: number;
  archiveStatus?: string;
  processStatus?: string;
  dynamicFields: Record<string, unknown>;
}

export interface ArchiveRecordUpdateCommand {
  fondsCode: string;
  archiveNo?: string;
  archiveYear?: number;
  archiveStatus?: string;
  processStatus?: string;
  dynamicFields: Record<string, unknown>;
}

export interface ArchiveRecordDto {
  id: number;
  fondsCode: string;
  categoryCode: string;
  categoryName: string;
  archiveNo?: string;
  archiveStatus: string;
  processStatus: string;
  archiveYear: number;
  lockedFlag: boolean;
  lockReason?: string;
  lockedBy?: number;
  lockedAt?: string;
}

export interface ArchiveRecordListDto {
  category?: ArchiveCategoryDto;
  fields: ArchiveFieldDto[];
  tableBuilt: boolean;
  rows: Record<string, unknown>[];
}

export interface ArchiveRecordDetailDto {
  record: ArchiveRecordDto;
  category: ArchiveCategoryDto;
  fields: ArchiveFieldDto[];
  dynamicFields: Record<string, unknown>;
}
