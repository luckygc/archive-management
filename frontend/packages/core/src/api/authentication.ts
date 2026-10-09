import type {
    AuthenticationEventDto,
    CurrentUserDto,
    CreateTotpCredentialRequest,
    CursorPageResponse,
    DisableTotpCredentialRequest,
    ListAuthenticationEventsParams,
    ListLoginSessionsParams,
    LoginRequest,
    LoginSessionDto,
    LoginResult,
    TotpCredentialStatusDto,
    TotpEnrollmentDto,
    TotpLoginChallengeDto,
    VerifyTotpLoginChallengeRequest,
} from "../types";
import { HttpClientError, httpClient } from "./client";

import { queryString } from "./query-string";

function pageUrl(path: string, params: { cursor?: string | null } & object) {
    const { cursor, ...query } = params;
    if (!cursor) {
        return `${path}${queryString(query)}`;
    }
    const link = new URL(cursor, "http://localhost");
    if (
        !cursor.startsWith("/") ||
        cursor.startsWith("//") ||
        link.origin !== "http://localhost" ||
        link.pathname !== path ||
        !link.searchParams.has("cursor") ||
        !link.searchParams.has("limit")
    ) {
        throw new Error("分页链接无效");
    }
    return `${link.pathname}${link.search}`;
}

export async function login(payload: LoginRequest): Promise<LoginResult> {
    const body = new URLSearchParams();
    body.set("username", payload.username);
    body.set("password", payload.password);

    const response = await httpClient.postResponse<LoginSessionDto | TotpLoginChallengeDto>(
        "/login-sessions",
        body,
        {
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
        },
    );
    if (response.status === 200) {
        return { status: 200, session: response.data as LoginSessionDto };
    }
    if (response.status === 202) {
        return { status: 202, challenge: response.data as TotpLoginChallengeDto };
    }
    throw new HttpClientError("登录服务返回了无法识别的响应", response.status);
}

export function verifyTotpLoginChallenge(payload: VerifyTotpLoginChallengeRequest) {
    return httpClient.post<LoginSessionDto>("/login-session-challenges:verifyTotp", payload);
}

export function createTotpEnrollment() {
    return httpClient.post<TotpEnrollmentDto>("/totp-enrollments");
}

export function createTotpCredential(payload: CreateTotpCredentialRequest) {
    return httpClient.post<TotpCredentialStatusDto>("/totp-credentials", payload);
}

export function disableTotpCredential(payload: DisableTotpCredentialRequest) {
    return httpClient.post<void>("/totp-credentials:disable", payload);
}

export function getCurrentUser() {
    return httpClient.get<CurrentUserDto>("/me");
}

export function logout(sessionId: string) {
    return deleteLoginSession(sessionId);
}

export function listLoginSessions(params: ListLoginSessionsParams = {}) {
    return httpClient.get<CursorPageResponse<LoginSessionDto>>(pageUrl("/login-sessions", params));
}

export function deleteLoginSession(sessionId: string) {
    return httpClient.delete<void>(`/login-sessions/${encodeURIComponent(sessionId)}`);
}

export function resetLoginFailureLimit(username: string) {
    return httpClient.post<void>(`/login-failure-limits/${encodeURIComponent(username)}:reset`);
}

export function listAuthenticationEvents(params: ListAuthenticationEventsParams = {}) {
    return httpClient.get<CursorPageResponse<AuthenticationEventDto>>(
        pageUrl("/authentication-events", params),
    );
}
