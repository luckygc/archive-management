import { afterEach, describe, expect, it, vi } from "vite-plus/test";

import type { CapWidget } from "cap-widget";

import {
    capFetch,
    capWidgetApiEndpoint,
    createCapVerificationController,
    initialCapVerificationState,
    reduceCapVerification,
} from "./capVerification";

describe("CAP verification state", () => {
    it("reduces solve, error and reset actions without framework state", () => {
        const initial = initialCapVerificationState();
        const solved = reduceCapVerification(initial, { type: "solve", token: "pow-token" });
        const failed = reduceCapVerification(solved, {
            type: "error",
            message: "challenge expired",
        });

        expect(solved).toEqual({
            powToken: "pow-token",
            securityMessage: "安全验证已完成",
        });
        expect(failed).toEqual({
            powToken: "",
            securityMessage: "安全验证失败：challenge expired",
        });
        expect(reduceCapVerification(failed, { type: "reset", message: "请重新验证" })).toEqual({
            powToken: "",
            securityMessage: "请重新验证",
        });
    });

    it("notifies framework adapters and resets the widget", () => {
        const reset = vi.fn();
        const widget = { reset } as unknown as CapWidget;
        const controller = createCapVerificationController(() => widget);
        const states: Array<{ powToken: string; securityMessage: string }> = [];
        const unsubscribe = controller.subscribe((state) => states.push(state));

        controller.handleSolve(new CustomEvent("solve", { detail: { token: "pow-token" } }));
        controller.reset("验证已失效");
        unsubscribe();
        controller.handleReset();

        expect(reset).toHaveBeenCalledOnce();
        expect(states).toEqual([
            { powToken: "pow-token", securityMessage: "安全验证已完成" },
            { powToken: "", securityMessage: "验证已失效" },
        ]);
        expect(controller.getState()).toEqual(initialCapVerificationState());
    });
});

describe("capFetch", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("rewrites cap-widget internal actions to project AIP endpoints", async () => {
        const requests: string[] = [];
        vi.stubGlobal(
            "fetch",
            vi.fn(async (input: RequestInfo | URL) => {
                requests.push(requestUrl(input));
                return new Response(JSON.stringify({ success: true }), {
                    headers: { "Content-Type": "application/json" },
                });
            }),
        );

        await capFetch(`${capWidgetApiEndpoint()}challenge`, { method: "POST" });
        await capFetch(`${capWidgetApiEndpoint()}redeem`, { method: "POST" });
        await capFetch(`${capWidgetApiEndpoint()}validateToken`, { method: "POST" });

        expect(requests).toEqual([
            "/api/v1/cap-challenges",
            "/api/v1/cap-tokens",
            "/api/v1/cap-tokens:validate",
        ]);
    });

    it("keeps unrelated requests unchanged", async () => {
        const requests: string[] = [];
        vi.stubGlobal(
            "fetch",
            vi.fn(async (input: RequestInfo | URL) => {
                requests.push(requestUrl(input));
                return new Response(JSON.stringify({ success: true }), {
                    headers: { "Content-Type": "application/json" },
                });
            }),
        );

        await capFetch("/api/v1/me");

        expect(requests).toEqual(["/api/v1/me"]);
    });

    it("does not attach username context to cap challenge requests", async () => {
        const requestBodies: string[] = [];
        vi.stubGlobal(
            "fetch",
            vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
                requestBodies.push(String(init?.body));
                return new Response(JSON.stringify({ success: true }), {
                    headers: { "Content-Type": "application/json" },
                });
            }),
        );

        await capFetch(`${capWidgetApiEndpoint()}challenge`, { method: "POST" });

        expect(requestBodies[0]).toBe("undefined");
    });
});

function requestUrl(input: RequestInfo | URL) {
    return input instanceof Request ? input.url : input.toString();
}
