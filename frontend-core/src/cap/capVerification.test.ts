import { afterEach, describe, expect, it, vi } from "vite-plus/test";

import { capFetch, capWidgetApiEndpoint, setCapChallengeContext } from "./capVerification";

describe("capFetch", () => {
    afterEach(() => {
        setCapChallengeContext({});
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

    it("adds login risk context to cap challenge requests", async () => {
        const requestBodies: string[] = [];
        setCapChallengeContext({
            username: "admin",
        });
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

        expect(JSON.parse(requestBodies[0])).toEqual({
            username: "admin",
        });
    });
});

function requestUrl(input: RequestInfo | URL) {
    return input instanceof Request ? input.url : input.toString();
}
