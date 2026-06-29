import { afterEach, describe, expect, it, vi } from "vite-plus/test";

import { capFetch, capWidgetApiEndpoint } from "./capVerification";

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
});

function requestUrl(input: RequestInfo | URL) {
    return input instanceof Request ? input.url : input.toString();
}
