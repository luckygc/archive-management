export type ArchiveFieldType = "TEXT" | "INTEGER" | "DECIMAL" | "DATE" | "DATETIME";
export type ArchiveFieldControl = "INPUT" | "TEXTAREA" | "NUMBER" | "DATE" | "DATETIME";
export type ArchiveTableStatus = "NOT_BUILT" | "BUILT";
export type ArchiveLayoutSurface = "TABLE" | "DETAIL" | "EDIT";
export type ArchiveLayoutScope = "PUBLIC" | "MINE" | "EFFECTIVE";
export type ArchiveLevel = "VOLUME" | "ITEM";
export type ArchiveManagementMode = "ITEM_ONLY" | "VOLUME_ITEM";
export type ArchiveElectronicStatus = "DRAFT" | "ARCHIVED" | "BORROWED";
export type ArchivePhysicalStatus =
  | "NONE"
  | "REGISTERED"
  | "TRANSFERRING"
  | "IN_STORAGE"
  | "BORROWED";

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
  managementMode: ArchiveManagementMode;
  volumeTableName?: string;
  itemTableName?: string;
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
  managementMode: ArchiveManagementMode;
  enabled: boolean;
  sortOrder: number;
}

export interface ArchiveFieldDto {
  id: number;
  categoryId: number;
  archiveLevel: ArchiveLevel;
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
  archiveLevel: ArchiveLevel;
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

export interface ArchiveUniqueConstraintFieldDto {
  fieldId: number;
  fieldOrder: number;
  archiveLevel: ArchiveLevel;
  fieldCode: string;
  fieldName: string;
  columnName: string;
}

export interface ArchiveUniqueConstraintDto {
  id: number;
  categoryId: number;
  archiveLevel: ArchiveLevel;
  constraintCode: string;
  constraintName: string;
  includeFonds: boolean;
  indexName: string;
  enabled: boolean;
  fields: ArchiveUniqueConstraintFieldDto[];
  createdAt: string;
  updatedAt: string;
}

export interface ArchiveUniqueConstraintCommand {
  archiveLevel: ArchiveLevel;
  constraintCode: string;
  constraintName: string;
  includeFonds: boolean;
  enabled: boolean;
  fieldIds: number[];
}

export interface ArchiveRecordQuery {
  categoryId?: number;
  archiveLevel?: ArchiveLevel;
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
  archiveLevel?: ArchiveLevel;
  parentId?: number;
  fondsCode: string;
  archiveNo?: string;
  archiveYear?: number;
  electronicStatus?: ArchiveElectronicStatus;
  physicalObject: ArchivePhysicalObjectCommand;
  dynamicFields: Record<string, unknown>;
}

export interface ArchiveRecordUpdateCommand {
  parentId?: number;
  fondsCode: string;
  archiveNo?: string;
  archiveYear?: number;
  electronicStatus?: ArchiveElectronicStatus;
  physicalObject: ArchivePhysicalObjectCommand;
  dynamicFields: Record<string, unknown>;
}

export interface ArchivePhysicalObjectCommand {
  physicalStatus: ArchivePhysicalStatus;
  boxNo?: string;
  locationNo?: string;
  barcode?: string;
  remark?: string;
}

export interface ArchiveRecordDto {
  id: number;
  archiveLevel: ArchiveLevel;
  parentId?: number;
  fondsCode: string;
  fondsName: string;
  categoryCode: string;
  categoryName: string;
  archiveNo?: string;
  electronicStatus: ArchiveElectronicStatus;
  archiveYear: number;
  lockedFlag: boolean;
  lockReason?: string;
  lockedBy?: number;
  lockedAt?: string;
}

export interface ArchivePhysicalObjectDto {
  id: number;
  archiveRecordId: number;
  physicalStatus: ArchivePhysicalStatus;
  boxNo?: string;
  locationNo?: string;
  barcode?: string;
  remark?: string;
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
  physicalObject?: ArchivePhysicalObjectDto;
}
