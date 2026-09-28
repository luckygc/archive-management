import { beforeEach, describe, expect, it, vi } from "vite-plus/test";

import {
    createTotpCredential,
    createTotpEnrollment,
    disableTotpCredential,
    getCurrentUser,
    listLoginSessions,
    login,
    resetLoginFailureLimit,
    verifyTotpLoginChallenge,
} from "./authentication";

const httpClientMock = vi.hoisted(() => ({
    delete: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    postResponse: vi.fn(),
}));

vi.mock("./client", () => ({
    httpClient: httpClientMock,
}));

describe("authentication API", () => {
    beforeEach(() => vi.clearAllMocks());

    it("loads current user from the canonical session endpoint", async () => {
        httpClientMock.get.mockResolvedValue({
            sessionId: "session-1",
            username: "admin",
            displayName: "系统管理员",
            roles: ["admin"],
            totpEnabled: false,
        });

        await getCurrentUser();

        expect(httpClientMock.get).toHaveBeenCalledWith("/me");
    });

    it("登录会话列表沿用服务端分页链接", async () => {
        const next = "/login-sessions?limit=50&cursor=opaque";
        await listLoginSessions({ limit: 100, cursor: next });

        expect(httpClientMock.get).toHaveBeenCalledWith(next);
        expect(() =>
            listLoginSessions({ cursor: "https://example.org/login-sessions?limit=50&cursor=x" }),
        ).toThrow("分页链接无效");
    });

    it("submits password login as a form request", async () => {
        httpClientMock.postResponse.mockResolvedValue({
            status: 200,
            data: {
                sessionId: "session-1",
                username: "admin",
                displayName: "系统管理员",
                roles: ["admin"],
                totpEnabled: false,
            },
        });

        await login({
            username: "admin",
            password: "secret",
        });

        expect(httpClientMock.postResponse).toHaveBeenCalledWith(
            "/login-sessions",
            expect.any(URLSearchParams),
            {
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                },
            },
        );
        const body = httpClientMock.postResponse.mock.calls[0]?.[1] as URLSearchParams;
        expect(body.get("username")).toBe("admin");
        expect(body.get("password")).toBe("secret");
    });

    it("preserves the HTTP 202 TOTP challenge branch", async () => {
        httpClientMock.postResponse.mockResolvedValue({
            status: 202,
            data: { challengeToken: "challenge-1", expiresAt: "2026-08-09T10:05:00Z" },
        });

        await expect(login({ username: "admin", password: "secret" })).resolves.toEqual({
            status: 202,
            challenge: { challengeToken: "challenge-1", expiresAt: "2026-08-09T10:05:00Z" },
        });
    });

    it("submits TOTP login verification as JSON", async () => {
        httpClientMock.post.mockResolvedValue({ sessionId: "session-1" });

        await verifyTotpLoginChallenge({ challengeToken: "challenge/1", code: "123456" });

        expect(httpClientMock.post).toHaveBeenCalledWith("/login-session-challenges:verifyTotp", {
            challengeToken: "challenge/1",
            code: "123456",
        });
    });

    it("uses local account TOTP enrollment and credential resources", async () => {
        httpClientMock.post.mockResolvedValue(undefined);

        await createTotpEnrollment();
        await createTotpCredential({
            enrollmentToken: "enrollment-1",
            currentPassword: "secret",
            code: "123456",
        });
        await disableTotpCredential({ currentPassword: "secret", code: "654321" });

        expect(httpClientMock.post).toHaveBeenNthCalledWith(1, "/totp-enrollments");
        expect(httpClientMock.post).toHaveBeenNthCalledWith(2, "/totp-credentials", {
            enrollmentToken: "enrollment-1",
            currentPassword: "secret",
            code: "123456",
        });
        expect(httpClientMock.post).toHaveBeenNthCalledWith(3, "/totp-credentials:disable", {
            currentPassword: "secret",
            code: "654321",
        });
    });

    it("resets login failure limit by username", async () => {
        httpClientMock.post.mockResolvedValue(undefined);

        await resetLoginFailureLimit("admin@example.com");

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/login-failure-limits/admin%40example.com:reset",
        );
    });
});
