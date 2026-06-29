export interface LoginCommand {
    username: string;
    password: string;
    powToken: string;
}

export interface CurrentUserDto {
    sessionId: string;
    username: string;
    displayName: string;
    roles: string[];
}

export interface ClientInfoDto {
    userAgent: string;
    browserName: string;
    browserVersion: string;
    osName: string;
    osVersion: string;
    deviceType: string;
}

export interface RequestContextDto {
    remoteAddress: string;
    host: string;
    forwarded: string;
    xForwardedFor: string;
    xRealIp: string;
}

export interface CursorPageDto<T> {
    items: T[];
    self?: string | null;
    prev?: string | null;
    next?: string | null;
    first?: string | null;
    total?: number | null;
}

export interface LoginSessionDto {
    sessionId: string;
    userId?: number | null;
    username: string;
    displayName: string;
    roles: string[];
    creationTime?: string | null;
    lastAccessTime?: string | null;
    expiresAt?: string | null;
    current: boolean;
    client: ClientInfoDto;
    request: RequestContextDto;
}

export interface AuthenticationEventDto {
    id?: number | null;
    eventType: string;
    userId?: number | null;
    username: string;
    displayName: string;
    sessionId: string;
    operatorUserId?: number | null;
    operatorUsername: string;
    failureReason: string;
    client: ClientInfoDto;
    request: RequestContextDto;
    occurredAt?: string | null;
}

export interface ListLoginSessionsParams {
    limit?: number;
    cursor?: string | null;
    requestTotal?: boolean;
}

export interface ListAuthenticationEventsParams {
    eventType?: string;
    username?: string;
    keyword?: string;
    occurredAfter?: string;
    occurredBefore?: string;
    limit?: number;
    cursor?: string | null;
    requestTotal?: boolean;
}
