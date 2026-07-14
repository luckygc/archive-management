export type ArchiveDataScopeType = "ALL" | "CONDITIONAL";
export type ArchiveDataScopeDimensionType =
    | "FONDS"
    | "CATEGORY"
    | "SECURITY_LEVEL"
    | "RETENTION_PERIOD";

export interface ArchiveDataScopeDto {
    id: number;
    scopeCode: string;
    scopeName: string;
    scopeType: ArchiveDataScopeType;
    dimensions: ArchiveDataScopeDimensionDto[];
    dynamicCondition?: ArchiveDataScopeDynamicCondition;
    enabled: boolean;
    description?: string;
}

export interface ArchiveDataScopeDimensionDto {
    dimensionType: ArchiveDataScopeDimensionType;
    targetId?: number;
    targetCode?: string;
    includeDescendants: boolean;
    sortOrder: number;
}

export interface ArchiveDataScopeDynamicCondition {
    dynamicFields: ArchiveDataScopeDynamicFieldCondition[];
}

export interface ArchiveDataScopeDynamicFieldCondition {
    categoryId: number;
    fieldCode: string;
    operator: "EQ" | "IN" | "IS_NULL" | "IS_NOT_NULL";
    values: string[];
}

export interface ArchiveDataScopeRequest {
    scopeCode: string;
    scopeName: string;
    scopeType: ArchiveDataScopeType;
    dimensions: ArchiveDataScopeDimensionRequest[];
    dynamicCondition?: ArchiveDataScopeDynamicCondition;
    enabled: boolean;
    description?: string;
}

export interface ArchiveDataScopeDimensionRequest {
    dimensionType: ArchiveDataScopeDimensionType;
    targetId?: number;
    targetCode?: string;
    includeDescendants: boolean;
}

export interface AuthorizationPermissionDto {
    permissionCode: string;
    permissionName: string;
    moduleCode: string;
    description: string;
}

export interface AuthorizationRoleDto {
    id: number;
    roleName: string;
    description?: string;
    enabled: boolean;
    createdAt: string;
}

export interface CreateAuthorizationRoleRequest {
    roleName: string;
    description?: string;
}

export interface UpdateAuthorizationRoleRequest {
    roleName?: string;
    description?: string;
    enabled?: boolean;
}

export interface CurrentUserPermissionsDto {
    permissionCodes: string[];
    superAdmin: boolean;
}

export interface RolePermissionsDto {
    roleId: number;
    permissionCodes: string[];
}

export interface RoleArchiveDataScopesDto {
    roleId: number;
    scopeIds: number[];
}

export interface UserArchiveDataScopesDto {
    userId: number;
    scopeIds: number[];
}

export interface DepartmentArchiveDataScopesDto {
    departmentId: number;
    scopeIds: number[];
}
