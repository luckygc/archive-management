const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
export const UNAUTHENTICATED_EVENT = "archive-management:unauthenticated";

interface ProblemDetailBody {
    title?: string;
    status?: number;
    detail?: string;
    code?: string;
    fieldViolations?: Array<{
        field?: string;
        message?: string;
    }>;
    traceId?: string;
}

export class HttpClientError extends Error {
    readonly status: number;
    readonly code?: string;
    readonly fieldViolations: Array<{ field?: string; message?: string }>;

    constructor(
        message: string,
        status: number,
        code?: string,
        fieldViolations: Array<{ field?: string; message?: string }> = [],
    ) {
        super(message);
        this.name = "HttpClientError";
        this.status = status;
        this.code = code;
        this.fieldViolations = fieldViolations;
    }
}

function readCookie(name: string) {
    if (typeof document === "undefined") {
        return undefined;
    }
    const prefix = `${name}=`;
    return document.cookie
        .split(";")
        .map((item) => item.trim())
        .find((item) => item.startsWith(prefix))
        ?.slice(prefix.length);
}

function isUnsafeMethod(method: string) {
    return !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method.toUpperCase());
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    const method = init.method ?? "GET";
    const csrfToken = readCookie(CSRF_COOKIE_NAME);
    if (isUnsafeMethod(method) && csrfToken && !headers.has(CSRF_HEADER_NAME)) {
        headers.set(CSRF_HEADER_NAME, decodeURIComponent(csrfToken));
    }

    const response = await fetch(path, {
        ...init,
        credentials: "include",
        headers,
    });

    if (!response.ok) {
        const error = await toHttpClientError(response);
        if (shouldNotifyUnauthenticated(path, error)) {
            window.dispatchEvent(
                new CustomEvent(UNAUTHENTICATED_EVENT, {
                    detail: {
                        path,
                        status: error.status,
                    },
                }),
            );
        }
        throw error;
    }

    if (response.status === 204) {
        return undefined as T;
    }

    return (await response.json()) as T;
}

function shouldNotifyUnauthenticated(path: string, error: HttpClientError) {
    return error.status === 401 && path !== "/api/v1/auth/session" && path !== "/api/v1/auth:login";
}

async function toHttpClientError(response: Response) {
    const contentType = response.headers.get("Content-Type") ?? "";
    const responseText = await response.text();
    if (contentType.includes("application/json") && responseText) {
        const body = parseJsonError(responseText);
        if (body) {
            const fieldViolations =
                body.fieldViolations?.filter((violation) => violation.message) ?? [];
            const message =
                fieldViolations.length > 0
                    ? fieldViolations.map((violation) => violation.message).join("；")
                    : body.detail || body.title;
            return new HttpClientError(
                message || `请求失败：${response.status}`,
                response.status,
                body.code,
                fieldViolations,
            );
        }
    }

    const message = responseText.trim();
    return new HttpClientError(
        message || defaultErrorMessage(response.status),
        response.status,
        defaultErrorCode(response.status),
    );
}

function parseJsonError(value: string) {
    try {
        return JSON.parse(value) as ProblemDetailBody;
    } catch {
        return undefined;
    }
}

export function errorMessage(error: unknown, fallback = "请求失败") {
    return error instanceof Error && error.message ? error.message : fallback;
}

function defaultErrorMessage(status: number) {
    if (status === 401) {
        return "登录状态已过期，请重新登录";
    }
    if (status === 403) {
        return "没有权限执行该操作";
    }
    if (status >= 500) {
        return "服务处理失败，请稍后重试";
    }
    return `请求失败：${status}`;
}

function defaultErrorCode(status: number) {
    if (status === 401) {
        return "UNAUTHENTICATED";
    }
    if (status === 403) {
        return "PERMISSION_DENIED";
    }
    return undefined;
}
