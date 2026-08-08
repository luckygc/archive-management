import type {
    AuthenticationEventDto,
    CurrentUserDto,
    CreateTotpCredentialRequest,
    CursorPageDto,
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

function queryString(params: object) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== null && value !== "") {
            search.set(key, String(value));
        }
    }
    const text = search.toString();
    return text ? `?${text}` : "";
}

export async function login(payload: LoginRequest): Promise<LoginResult> {
    const body = new URLSearchParams();
    body.set("username", payload.username);
    body.set("password", payload.password);
    body.set("powToken", payload.powToken);

    const response = await httpClient.postResponse<LoginSessionDto | TotpLoginChallengeDto>(
        "/api/v1/login-sessions",
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
    return httpClient.post<LoginSessionDto>("/api/v1/login-session-challenges:verifyTotp", payload);
}

export function createTotpEnrollment() {
    return httpClient.post<TotpEnrollmentDto>("/api/v1/totp-enrollments");
}

export function createTotpCredential(payload: CreateTotpCredentialRequest) {
    return httpClient.post<TotpCredentialStatusDto>("/api/v1/totp-credentials", payload);
}

export function disableTotpCredential(payload: DisableTotpCredentialRequest) {
    return httpClient.post<void>("/api/v1/totp-credentials:disable", payload);
}

export function getCurrentUser() {
    return httpClient.get<CurrentUserDto>("/api/v1/me");
}

export function logout(sessionId: string) {
    return deleteLoginSession(sessionId);
}

export function listLoginSessions(params: ListLoginSessionsParams = {}) {
    return httpClient.get<CursorPageDto<LoginSessionDto>>(
        `/api/v1/login-sessions${queryString(params)}`,
    );
}

export function deleteLoginSession(sessionId: string) {
    return httpClient.delete<void>(`/api/v1/login-sessions/${encodeURIComponent(sessionId)}`);
}

export function resetLoginFailureLimit(username: string) {
    return httpClient.post<void>(
        `/api/v1/login-failure-limits/${encodeURIComponent(username)}:reset`,
    );
}

export function listAuthenticationEvents(params: ListAuthenticationEventsParams = {}) {
    return httpClient.get<CursorPageDto<AuthenticationEventDto>>(
        `/api/v1/authentication-events${queryString(params)}`,
    );
}
