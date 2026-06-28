import { useCallback, useState } from "react";
import type { RefObject } from "react";
import type { CapErrorEvent, CapSolveEvent, CapWidget } from "cap-widget";

const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const CAP_WIDGET_API_ENDPOINT = "/api/v1/auth/cap-challenges/";
const CAP_WIDGET_ENDPOINT_PREFIX = "/api/v1/auth/cap-challenges/";
const CAP_WIDGET_ACTION_PATHS: Record<string, string> = {
    challenge: "/api/v1/auth/cap-challenges",
    redeem: "/api/v1/auth/cap-tokens",
    validateToken: "/api/v1/auth/cap-tokens:validate",
};

interface ProblemDetailBody {
    title?: string;
    detail?: string;
    fieldViolations?: Array<{ message?: string }>;
}

export function useCapVerification(capWidgetRef: RefObject<CapWidget | null>) {
    installCapFetch();

    const [powToken, setPowToken] = useState("");
    const [securityMessage, setSecurityMessage] = useState("请完成安全验证");

    const resetCapWidget = useCallback(
        (message = "请完成安全验证") => {
            setPowToken("");
            capWidgetRef.current?.reset();
            setSecurityMessage(message);
        },
        [capWidgetRef],
    );

    const handleCapSolve = useCallback((event: Event) => {
        setPowToken((event as CapSolveEvent).detail.token);
        setSecurityMessage("安全验证已完成");
    }, []);

    const handleCapReset = useCallback(() => {
        setPowToken("");
        setSecurityMessage("请完成安全验证");
    }, []);

    const handleCapError = useCallback((event: Event) => {
        setPowToken("");
        const detail = (event as CapErrorEvent).detail;
        setSecurityMessage(
            detail?.message ? `安全验证失败：${detail.message}` : "安全验证失败，请重试",
        );
    }, []);

    return {
        powToken,
        securityMessage,
        resetCapWidget,
        handleCapSolve,
        handleCapReset,
        handleCapError,
    };
}

export async function capFetch(input: RequestInfo | URL, init: RequestInit = {}) {
    const response = await fetch(rewriteCapWidgetRequest(input), withCredentialsAndCsrf(init));
    if (response.ok) {
        return response;
    }
    return capErrorResponse(response);
}

export function capWidgetApiEndpoint() {
    return CAP_WIDGET_API_ENDPOINT;
}

export function installCapFetch() {
    if (typeof window !== "undefined") {
        window.CAP_CUSTOM_FETCH = capFetch;
    }
}

function withCredentialsAndCsrf(init: RequestInit) {
    const headers = new Headers(init.headers);
    if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    const method = init.method ?? "GET";
    const csrfToken = readCookie(CSRF_COOKIE_NAME);
    if (isUnsafeMethod(method) && csrfToken && !headers.has(CSRF_HEADER_NAME)) {
        headers.set(CSRF_HEADER_NAME, decodeURIComponent(csrfToken));
    }
    return {
        ...init,
        credentials: "include" as RequestCredentials,
        headers,
    };
}

function rewriteCapWidgetRequest(input: RequestInfo | URL) {
    if (input instanceof Request) {
        const rewrittenUrl = rewriteCapWidgetUrl(input.url);
        const requestUrl = new URL(rewrittenUrl.toString(), input.url).toString();
        return requestUrl === input.url ? input : new Request(requestUrl, input);
    }

    if (input instanceof URL) {
        return new URL(rewriteCapWidgetUrl(input));
    }

    return rewriteCapWidgetUrl(input);
}

function rewriteCapWidgetUrl(input: string | URL) {
    const origin = typeof window === "undefined" ? "http://localhost" : window.location.origin;
    const parsedUrl = new URL(input, origin);
    if (!parsedUrl.pathname.startsWith(CAP_WIDGET_ENDPOINT_PREFIX)) {
        return input instanceof URL ? parsedUrl : input;
    }
    const action = parsedUrl.pathname.slice(CAP_WIDGET_ENDPOINT_PREFIX.length);
    const mappedPath = CAP_WIDGET_ACTION_PATHS[action];
    if (mappedPath) {
        parsedUrl.pathname = mappedPath;
    }
    return input instanceof URL
        ? parsedUrl
        : `${parsedUrl.pathname}${parsedUrl.search}${parsedUrl.hash}`;
}

async function capErrorResponse(response: Response) {
    const responseText = await response.text();
    const message =
        problemDetailMessage(responseText) ?? (responseText.trim() || "安全验证失败，请重试");
    return new Response(JSON.stringify({ success: false, error: message }), {
        status: response.status,
        headers: { "Content-Type": "application/json" },
    });
}

function problemDetailMessage(value: string) {
    if (!value) {
        return undefined;
    }
    try {
        const body = JSON.parse(value) as ProblemDetailBody;
        const fieldMessages =
            body.fieldViolations?.map((violation) => violation.message).filter(Boolean) ?? [];
        return fieldMessages.length > 0
            ? fieldMessages.join("；")
            : body.detail || body.title || undefined;
    } catch {
        return undefined;
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
