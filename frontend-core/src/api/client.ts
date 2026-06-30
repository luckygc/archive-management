import axios, { AxiosHeaders } from "axios";
import type { AxiosRequestConfig, Method, RawAxiosRequestHeaders } from "axios";

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

type RequestBody = BodyInit | object | null | undefined;
type ObjectBodyInit = Exclude<BodyInit, string>;

const axiosClient = axios.create({
    adapter: "fetch",
    withCredentials: true,
});

export const httpClient = {
    request: requestJson,
    get<T>(path: string, init: RequestInit = {}) {
        return requestJson<T>(path, { ...init, method: "GET" });
    },
    post<T>(path: string, body?: RequestBody, init: RequestInit = {}) {
        return requestJson<T>(path, withBody(init, "POST", body));
    },
    patch<T>(path: string, body?: RequestBody, init: RequestInit = {}) {
        return requestJson<T>(path, withBody(init, "PATCH", body));
    },
    put<T>(path: string, body?: RequestBody, init: RequestInit = {}) {
        return requestJson<T>(path, withBody(init, "PUT", body));
    },
    delete<T>(path: string, init: RequestInit = {}) {
        return requestJson<T>(path, { ...init, method: "DELETE" });
    },
    download(path: string, init: RequestInit = {}) {
        return downloadLink(path, init);
    },
};

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
    try {
        const response = await axiosClient.request<T>(axiosConfig(path, init));

        if (response.status === 204) {
            return undefined as T;
        }

        return response.data;
    } catch (error) {
        const clientError = toHttpClientError(error);
        if (shouldNotifyUnauthenticated(path, clientError) && typeof window !== "undefined") {
            window.dispatchEvent(
                new CustomEvent(UNAUTHENTICATED_EVENT, {
                    detail: {
                        path,
                        status: clientError.status,
                    },
                }),
            );
        }
        throw clientError;
    }
}

function withBody(init: RequestInit, method: string, body: RequestBody): RequestInit {
    return {
        ...init,
        method,
        body: serializeBody(body),
    };
}

function serializeBody(body: RequestBody): BodyInit | null | undefined {
    if (body === undefined || body === null || typeof body !== "object" || isBodyInit(body)) {
        return body;
    }
    return JSON.stringify(body);
}

function isBodyInit(body: object): body is ObjectBodyInit {
    return (
        (typeof FormData !== "undefined" && body instanceof FormData) ||
        (typeof URLSearchParams !== "undefined" && body instanceof URLSearchParams) ||
        (typeof Blob !== "undefined" && body instanceof Blob) ||
        (typeof ArrayBuffer !== "undefined" && body instanceof ArrayBuffer)
    );
}

export interface DownloadLink {
    href: string;
}

function downloadLink(path: string, init: RequestInit = {}): DownloadLink {
    const method = init.method ?? "GET";
    if (method.toUpperCase() !== "GET" || init.body) {
        throw new Error("下载必须使用浏览器可直接打开的 GET 链接");
    }
    return { href: path };
}

function axiosConfig(path: string, init: RequestInit): AxiosRequestConfig {
    const headers = requestHeaders(init);
    const method = (init.method ?? "GET") as Method;
    const data = serializeBody(init.body);
    return {
        url: path,
        method,
        data,
        headers,
        signal: init.signal ?? undefined,
        withCredentials: true,
    };
}

function requestHeaders(init: RequestInit): RawAxiosRequestHeaders {
    const headers = new AxiosHeaders();
    new Headers(init.headers).forEach((value, key) => {
        headers.set(key, value);
    });
    if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    const method = init.method ?? "GET";
    const csrfToken = readCookie(CSRF_COOKIE_NAME);
    if (isUnsafeMethod(method) && csrfToken && !headers.has(CSRF_HEADER_NAME)) {
        headers.set(CSRF_HEADER_NAME, decodeURIComponent(csrfToken));
    }
    return headers.toJSON() as RawAxiosRequestHeaders;
}

function shouldNotifyUnauthenticated(path: string, error: HttpClientError) {
    return error.status === 401 && path !== "/api/v1/me" && path !== "/api/v1/login-sessions";
}

function toHttpClientError(error: unknown) {
    if (axios.isAxiosError(error) && error.response) {
        const response = error.response;
        const body = problemDetailBody(response.data);
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

        const message = typeof response.data === "string" ? response.data.trim() : "";
        return new HttpClientError(
            message || defaultErrorMessage(response.status),
            response.status,
            defaultErrorCode(response.status),
        );
    }

    if (axios.isAxiosError(error)) {
        return new HttpClientError(error.message || "请求失败", 0);
    }

    return error instanceof HttpClientError
        ? error
        : new HttpClientError(error instanceof Error ? error.message : "请求失败", 0);
}

function problemDetailBody(value: unknown): ProblemDetailBody | undefined {
    if (typeof value === "object" && value !== null) {
        return value as ProblemDetailBody;
    }
    if (typeof value !== "string" || !value) {
        return undefined;
    }
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
