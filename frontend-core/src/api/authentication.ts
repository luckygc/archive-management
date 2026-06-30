import type {
    AuthenticationEventDto,
    CurrentUserDto,
    CursorPageDto,
    ListAuthenticationEventsParams,
    ListLoginSessionsParams,
    LoginRequest,
    LoginSessionDto,
} from "../types";
import { httpClient } from "./client";

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

export function login(payload: LoginRequest) {
    const body = new URLSearchParams();
    body.set("username", payload.username);
    body.set("password", payload.password);
    body.set("powToken", payload.powToken);

    return httpClient.post<LoginSessionDto>("/api/v1/login-sessions", body, {
        headers: {
            "Content-Type": "application/x-www-form-urlencoded",
        },
    });
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

export function listAuthenticationEvents(params: ListAuthenticationEventsParams = {}) {
    return httpClient.get<CursorPageDto<AuthenticationEventDto>>(
        `/api/v1/authentication-events${queryString(params)}`,
    );
}
