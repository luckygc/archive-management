import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { ArchiveFondsPage } from "./ArchiveFondsPage";

beforeEach(() => {
    vi.stubGlobal(
        "fetch",
        vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
            const url = requestUrl(input);
            if (url === "/api/v1/archive-fonds" && (!init?.method || init.method === "GET")) {
                return jsonResponse({
                    items: [
                        createFonds({
                            id: 1,
                            fondsCode: "HD",
                            fondsName: "华东公司",
                            enabled: true,
                        }),
                        createFonds({
                            id: 2,
                            fondsCode: "CW",
                            fondsName: "财务部",
                            enabled: false,
                        }),
                    ],
                });
            }
            if (url === "/api/v1/archive-fonds/1" && init?.method === "PATCH") {
                expect(JSON.parse(requestBodyText(init?.body))).toMatchObject({
                    enabled: false,
                    fondsCode: "HD",
                    fondsName: "华东公司",
                    sortOrder: 10,
                });
                return jsonResponse(
                    createFonds({ id: 1, fondsCode: "HD", fondsName: "华东公司", enabled: false }),
                );
            }
            return jsonResponse({}, 404);
        }),
    );
});

afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
});

describe("ArchiveFondsPage", () => {
    it("toggles fonds enabled state through backend update", async () => {
        render(
            <QueryClientProvider client={new QueryClient()}>
                <ArchiveFondsPage />
            </QueryClientProvider>,
        );

        const switchButton = await screen.findByRole("switch", { name: "停用全宗：华东公司" });
        expect(switchButton.getAttribute("aria-checked")).toBe("true");

        fireEvent.click(switchButton);

        await waitFor(() => {
            expect(fetch).toHaveBeenCalledWith(
                "/api/v1/archive-fonds/1",
                expect.objectContaining({ method: "PATCH" }),
            );
        });
    });
});

function requestUrl(input: RequestInfo | URL) {
    const value = input instanceof Request ? input.url : input.toString();
    const url = new URL(value, window.location.origin);
    return `${url.pathname}${url.search}`;
}

function requestBodyText(body: BodyInit | null | undefined) {
    return typeof body === "string" ? body : "";
}

function jsonResponse(body: unknown, status = 200) {
    return Promise.resolve(
        new Response(JSON.stringify(body), {
            status,
            headers: { "Content-Type": "application/json" },
        }),
    );
}

function createFonds(values: {
    id: number;
    fondsCode: string;
    fondsName: string;
    enabled: boolean;
}) {
    return {
        createdAt: "2026-06-27T00:00:00Z",
        sortOrder: 10,
        updatedAt: "2026-06-27T00:00:00Z",
        ...values,
    };
}
