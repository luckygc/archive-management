export type ServiceAccountStatus = "ACTIVE" | "DISABLED";
export type AccessTokenType = "SERVICE_ACCOUNT" | "USER";

export interface ServiceAccountDto {
    id: number;
    name: string;
    description?: string;
    ownerUserId: number;
    ownerDisplayName: string;
    status: ServiceAccountStatus;
    roleIds: number[];
    createdAt: string;
}

export interface CreateServiceAccountRequest {
    name: string;
    description?: string;
    ownerUserId: number;
    roleIds?: number[];
}

export interface UpdateServiceAccountRequest {
    name?: string;
    description?: string | null;
    ownerUserId?: number;
    status?: ServiceAccountStatus;
}

export interface AccessTokenDto {
    id: number;
    name: string;
    tokenType: AccessTokenType;
    serviceAccountId?: number;
    userId?: number;
    tokenPrefix: string;
    scopes: string[];
    expiresAt: string;
    revokedAt?: string;
    lastUsedAt?: string;
    createdAt: string;
}

export interface IssuedAccessTokenDto extends AccessTokenDto {
    secret: string;
}

export interface CreateAccessTokenRequest {
    name: string;
    expiresAt: string;
    scopes?: string[];
}

export interface AccessTokenCallDto {
    id: number;
    tokenId?: number;
    tokenPrefix?: string;
    callerType: string;
    serviceAccountId?: number;
    representedUserId?: number;
    httpMethod: string;
    requestPath: string;
    statusCode: number;
    durationMs: number;
    sourceIp?: string;
    traceId?: string;
    failureReason?: string;
    occurredAt: string;
}
