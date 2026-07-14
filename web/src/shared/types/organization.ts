export interface OrganizationDepartmentDto {
    id: number;
    departmentCode: string;
    departmentName: string;
    parentId?: number;
    enabled: boolean;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
}

export interface CreateOrganizationDepartmentRequest {
    departmentCode?: string;
    departmentName?: string;
    parentId?: number | null;
    enabled?: boolean;
    sortOrder?: number;
}

export interface UpdateOrganizationDepartmentRequest {
    departmentCode?: string;
    departmentName?: string;
    parentId?: number | null;
    enabled?: boolean;
    sortOrder?: number;
}
