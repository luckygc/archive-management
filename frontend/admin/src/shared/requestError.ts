import { errorMessage, HttpClientError } from "@archive-management/frontend-core/api";

export function requestErrorMessage(error: unknown, fallback: string) {
    return withRequestTraceId(errorMessage(error, fallback), error);
}

export function withRequestTraceId(message: string, error: unknown) {
    return error instanceof HttpClientError && error.traceId
        ? `${message}（追踪 ID：${error.traceId}）`
        : message;
}

export function isCursorFieldViolation(error: unknown) {
    return (
        error instanceof HttpClientError &&
        error.fieldViolations.some((violation) => violation.field === "cursor")
    );
}
