import { describe, expect, it, vi } from "vite-plus/test";

import { httpClient } from "./client";

describe("httpClient", () => {
    it("generates browser download links without reading response bodies", () => {
        const fetchSpy = vi.spyOn(window, "fetch");

        expect(httpClient.download("/api/v1/files/10/content")).toEqual({
            href: "/api/v1/files/10/content",
        });
        expect(fetchSpy).not.toHaveBeenCalled();

        fetchSpy.mockRestore();
    });
});
