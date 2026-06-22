const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
export const UNAUTHENTICATED_EVENT = "archive-management:unauthenticated";

interface GoogleApiErrorDetail {
  "@type"?: string;
  fieldViolations?: Array<{
    field?: string;
    description?: string;
  }>;
}

interface GoogleApiErrorBody {
  error?: {
    code?: number;
    message?: string;
    status?: string;
    details?: GoogleApiErrorDetail[];
  };
}

export class HttpClientError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldViolations: Array<{ field?: string; description?: string }>;

  constructor(
    message: string,
    status: number,
    code?: string,
    fieldViolations: Array<{ field?: string; description?: string }> = [],
  ) {
    super(message);
    this.name = "HttpClientError";
    this.status = status;
    this.code = code;
    this.fieldViolations = fieldViolations;
  }
}

function readCookie(name: string) {
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
    const error = body?.error;
    if (error) {
      const fieldViolations =
        error.details
          ?.flatMap((detail) => detail.fieldViolations ?? [])
          .filter((violation) => violation.description) ?? [];
      const message =
        fieldViolations.length > 0
          ? fieldViolations.map((violation) => violation.description).join("；")
          : error.message;
      return new HttpClientError(
        message || `请求失败：${response.status}`,
        response.status,
        error.status,
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
    return JSON.parse(value) as GoogleApiErrorBody;
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
