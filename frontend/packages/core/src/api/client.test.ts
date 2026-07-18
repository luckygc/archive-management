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

    it("preserves ProblemDetail field violations and trace id", async () => {
        const fetchSpy = vi.spyOn(window, "fetch").mockResolvedValue(
            new Response(
                JSON.stringify({
                    title: "请求参数错误",
                    status: 400,
                    detail: "字段校验失败",
                    code: "INVALID_ARGUMENT",
                    fieldViolations: [{ field: "archiveNo", message: "档号已存在" }],
                    traceId: "trace-task-4",
                }),
                { status: 400, headers: { "content-type": "application/problem+json" } },
            ),
        );

        await expect(
            httpClient.patch("http://localhost/api/v1/archive-items/9", {}),
        ).rejects.toMatchObject({
            code: "INVALID_ARGUMENT",
            fieldViolations: [{ field: "archiveNo", message: "档号已存在" }],
            traceId: "trace-task-4",
        });
        fetchSpy.mockRestore();
    });
});
