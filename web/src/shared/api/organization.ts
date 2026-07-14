import { httpClient } from "@archive-management/frontend-core/api";
import type { CollectionResponse } from "../types/pagination";
import type {
    CreateOrganizationDepartmentRequest,
    OrganizationDepartmentDto,
    UpdateOrganizationDepartmentRequest,
} from "../types/organization";
import { queryString } from "./query-string";

export function listOrganizationDepartments(enabled?: boolean) {
    return httpClient.get<CollectionResponse<OrganizationDepartmentDto>>(
        `/api/v1/organization-departments${queryString({ enabled })}`,
    );
}

export function getOrganizationDepartment(id: number) {
    return httpClient.get<OrganizationDepartmentDto>(`/api/v1/organization-departments/${id}`);
}

export function createOrganizationDepartment(payload: CreateOrganizationDepartmentRequest) {
    return httpClient.post<OrganizationDepartmentDto>("/api/v1/organization-departments", payload);
}

export function updateOrganizationDepartment(
    id: number,
    payload: UpdateOrganizationDepartmentRequest,
) {
    return httpClient.patch<OrganizationDepartmentDto>(
        `/api/v1/organization-departments/${id}`,
        payload,
    );
}
