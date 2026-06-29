import type {
    AuthenticationEventDto,
    CurrentUserDto,
    CursorPageDto,
    ListAuthenticationEventsParams,
    ListLoginSessionsParams,
    LoginCommand,
    LoginSessionDto,
} from "../types";
import { request } from "./client";

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

export function login(command: LoginCommand) {
    const body = new URLSearchParams();
    body.set("username", command.username);
    body.set("password", command.password);
    body.set("powToken", command.powToken);

    return request<LoginSessionDto>("/api/v1/login-sessions", {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded",
        },
        body,
    });
}

export function getCurrentUser() {
    return request<CurrentUserDto>("/api/v1/me");
}

export function logout(sessionId: string) {
    return deleteLoginSession(sessionId);
}

export function listLoginSessions(params: ListLoginSessionsParams = {}) {
    return request<CursorPageDto<LoginSessionDto>>(`/api/v1/login-sessions${queryString(params)}`);
}

export function deleteLoginSession(sessionId: string) {
    return request<void>(`/api/v1/login-sessions/${encodeURIComponent(sessionId)}`, {
        method: "DELETE",
    });
}

export function listAuthenticationEvents(params: ListAuthenticationEventsParams = {}) {
    return request<CursorPageDto<AuthenticationEventDto>>(
        `/api/v1/authentication-events${queryString(params)}`,
    );
}
