export interface AuthenticationUserDto {
    id: number;
    username: string;
    displayName: string;
    email?: string;
    mobilePhone?: string;
    departmentId?: number;
    departmentCode?: string;
    departmentName?: string;
    enabled: boolean;
    createdAt: string;
}

export interface AuthenticationUserOptionDto {
    id: number;
    username: string;
    displayName: string;
}

export interface AuthenticationUserDetailDto extends AuthenticationUserDto {
    roles: RoleSummaryDto[];
}

export interface RoleSummaryDto {
    id: number;
    roleName: string;
}

export interface CreateAuthenticationUserRequest {
    username: string;
    password: string;
    displayName: string;
    email?: string;
    mobilePhone?: string;
    departmentId?: number | null;
}

export interface UpdateAuthenticationUserRequest {
    displayName?: string;
    email?: string | null;
    mobilePhone?: string | null;
    departmentId?: number | null;
    enabled?: boolean;
}

export interface ResetPasswordRequest {
    newPassword: string;
}

export interface SaveUserRolesRequest {
    roleIds: number[];
}
