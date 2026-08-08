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

    it("returns the HTTP status when a caller needs to distinguish accepted responses", async () => {
        const fetchSpy = vi.spyOn(window, "fetch").mockResolvedValue(
            new Response(JSON.stringify({ challengeToken: "challenge-1", expiresAt: "soon" }), {
                status: 202,
                headers: { "content-type": "application/json" },
            }),
        );

        await expect(
            httpClient.postResponse("http://localhost/api/v1/login-sessions", {}),
        ).resolves.toEqual({
            status: 202,
            data: { challengeToken: "challenge-1", expiresAt: "soon" },
        });
        fetchSpy.mockRestore();
    });

    it("does not treat an invalid anonymous TOTP challenge as an expired session", async () => {
        const unauthenticated = vi.fn();
        window.addEventListener("archive-management:unauthenticated", unauthenticated);
        const fetchSpy = vi.spyOn(window, "fetch").mockResolvedValue(
            new Response(
                JSON.stringify({
                    title: "认证失败",
                    status: 401,
                    detail: "账号或凭证错误",
                    code: "TOTP_CODE_INVALID",
                }),
                { status: 401, headers: { "content-type": "application/problem+json" } },
            ),
        );

        await expect(
            httpClient.post("http://localhost/api/v1/login-session-challenges:verifyTotp", {
                challengeToken: "challenge-1",
                code: "123456",
            }),
        ).rejects.toMatchObject({ code: "TOTP_CODE_INVALID" });
        expect(unauthenticated).not.toHaveBeenCalled();

        window.removeEventListener("archive-management:unauthenticated", unauthenticated);
        fetchSpy.mockRestore();
    });
});
