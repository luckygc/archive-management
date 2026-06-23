import { request } from "./client";
import type {
  ArchiveCategoryCommand,
  ArchiveCategoryDto,
  ArchiveFieldCommand,
  ArchiveFieldDto,
  ArchiveFieldLayoutCommand,
  ArchiveFieldLayoutDto,
  ArchiveLevel,
  ArchiveLayoutSurface,
  ArchiveFondsCommand,
  ArchiveFondsDto,
  ArchiveRecordCommand,
  ArchiveRecordDetailDto,
  ArchiveRecordDto,
  ArchiveRecordListDto,
  ArchiveRecordQuery,
  ArchiveRecordUpdateCommand,
  ArchiveUniqueConstraintCommand,
  ArchiveUniqueConstraintDto,
} from "../types/archive";

function queryString(params: Record<string, string | number | boolean | undefined>) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export function listArchiveFonds(enabled?: boolean) {
  return request<ArchiveFondsDto[]>(`/api/v1/archive-fonds${queryString({ enabled })}`);
}

export function createArchiveFonds(command: ArchiveFondsCommand) {
  return request<ArchiveFondsDto>("/api/v1/archive-fonds", {
    method: "POST",
    body: JSON.stringify(command),
  });
}

export function updateArchiveFonds(id: number, command: ArchiveFondsCommand) {
  return request<ArchiveFondsDto>(`/api/v1/archive-fonds/${id}`, {
    method: "PATCH",
    body: JSON.stringify(command),
  });
}

export function deleteArchiveFonds(id: number) {
  return request<void>(`/api/v1/archive-fonds/${id}`, {
    method: "DELETE",
  });
}

export function listArchiveCategories(enabled?: boolean) {
  return request<ArchiveCategoryDto[]>(`/api/v1/archive-categories${queryString({ enabled })}`);
}

export function createArchiveCategory(command: ArchiveCategoryCommand) {
  return request<ArchiveCategoryDto>("/api/v1/archive-categories", {
    method: "POST",
    body: JSON.stringify(command),
  });
}

export function updateArchiveCategory(id: number, command: ArchiveCategoryCommand) {
  return request<ArchiveCategoryDto>(`/api/v1/archive-categories/${id}`, {
    method: "PATCH",
    body: JSON.stringify(command),
  });
}

export function deleteArchiveCategory(id: number) {
  return request<void>(`/api/v1/archive-categories/${id}`, {
    method: "DELETE",
  });
}

export function listArchiveFields(categoryId: number) {
  return request<ArchiveFieldDto[]>(`/api/v1/archive-categories/${categoryId}/fields`);
}

export function createArchiveField(categoryId: number, command: ArchiveFieldCommand) {
  return request<ArchiveFieldDto>(`/api/v1/archive-categories/${categoryId}/fields`, {
    method: "POST",
    body: JSON.stringify(command),
  });
}

export function updateArchiveField(
  categoryId: number,
  fieldId: number,
  command: ArchiveFieldCommand,
) {
  return request<ArchiveFieldDto>(`/api/v1/archive-categories/${categoryId}/fields/${fieldId}`, {
    method: "PATCH",
    body: JSON.stringify(command),
  });
}

export function deleteArchiveField(categoryId: number, fieldId: number) {
  return request<void>(`/api/v1/archive-categories/${categoryId}/fields/${fieldId}`, {
    method: "DELETE",
  });
}

export function getArchiveCategoryLayout(
  categoryId: number,
  surface: ArchiveLayoutSurface,
  archiveLevel?: ArchiveLevel,
) {
  return request<ArchiveFieldLayoutDto>(
    `/api/v1/archive-categories/${categoryId}/layouts/${surface}${queryString({ archiveLevel })}`,
  );
}

export function savePublicArchiveCategoryLayout(
  categoryId: number,
  surface: ArchiveLayoutSurface,
  command: ArchiveFieldLayoutCommand,
  archiveLevel?: ArchiveLevel,
) {
  return request<ArchiveFieldLayoutDto>(
    `/api/v1/archive-categories/${categoryId}/layouts/${surface}${queryString({ archiveLevel })}`,
    {
      method: "PATCH",
      body: JSON.stringify(command),
    },
  );
}

export function buildArchiveCategoryTable(categoryId: number, archiveLevel?: ArchiveLevel) {
  return request<ArchiveCategoryDto>(
    `/api/v1/archive-categories/${categoryId}:buildTable${queryString({ archiveLevel })}`,
    {
      method: "POST",
    },
  );
}

export function listArchiveUniqueConstraints(categoryId: number) {
  return request<ArchiveUniqueConstraintDto[]>(
    `/api/v1/archive-categories/${categoryId}/unique-constraints`,
  );
}

export function createArchiveUniqueConstraint(
  categoryId: number,
  command: ArchiveUniqueConstraintCommand,
) {
  return request<ArchiveUniqueConstraintDto>(
    `/api/v1/archive-categories/${categoryId}/unique-constraints`,
    {
      method: "POST",
      body: JSON.stringify(command),
    },
  );
}

export function updateArchiveUniqueConstraint(
  categoryId: number,
  constraintId: number,
  command: ArchiveUniqueConstraintCommand,
) {
  return request<ArchiveUniqueConstraintDto>(
    `/api/v1/archive-categories/${categoryId}/unique-constraints/${constraintId}`,
    {
      method: "PATCH",
      body: JSON.stringify(command),
    },
  );
}

export function deleteArchiveUniqueConstraint(categoryId: number, constraintId: number) {
  return request<void>(
    `/api/v1/archive-categories/${categoryId}/unique-constraints/${constraintId}`,
    {
      method: "DELETE",
    },
  );
}

export function listArchiveRecords(params: {
  categoryId?: number;
  archiveLevel?: ArchiveLevel;
  fondsCode?: string;
}) {
  return request<ArchiveRecordListDto>(`/api/v1/archive-records${queryString(params)}`);
}

export function searchArchiveRecords(query: ArchiveRecordQuery) {
  return request<ArchiveRecordListDto>("/api/v1/archive-records:search", {
    method: "POST",
    body: JSON.stringify(query),
  });
}

export function createArchiveRecord(command: ArchiveRecordCommand) {
  return request<ArchiveRecordDto>("/api/v1/archive-records", {
    method: "POST",
    body: JSON.stringify(command),
  });
}

export function getArchiveRecord(id: string, surface?: ArchiveLayoutSurface) {
  return request<ArchiveRecordDetailDto>(
    `/api/v1/archive-records/${id}${queryString({ surface })}`,
  );
}

export function updateArchiveRecord(id: string, command: ArchiveRecordUpdateCommand) {
  return request<ArchiveRecordDetailDto>(`/api/v1/archive-records/${id}`, {
    method: "PATCH",
    body: JSON.stringify(command),
  });
}

export function lockArchiveRecord(id: string, reason?: string) {
  return request<ArchiveRecordDto>(`/api/v1/archive-records/${id}:lock`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

export function unlockArchiveRecord(id: string) {
  return request<ArchiveRecordDto>(`/api/v1/archive-records/${id}:unlock`, {
    method: "POST",
  });
}
