import { beforeEach, describe, expect, it, vi } from "vite-plus/test";

import {
    createTotpCredential,
    createTotpEnrollment,
    disableTotpCredential,
    getCurrentUser,
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

        expect(httpClientMock.get).toHaveBeenCalledWith("/api/v1/me");
    });

    it("submits password login as a form request with the CAP token", async () => {
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
            powToken: "pow-token-1",
        });

        expect(httpClientMock.postResponse).toHaveBeenCalledWith(
            "/api/v1/login-sessions",
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
        expect(body.get("powToken")).toBe("pow-token-1");
    });

    it("preserves the HTTP 202 TOTP challenge branch", async () => {
        httpClientMock.postResponse.mockResolvedValue({
            status: 202,
            data: { challengeToken: "challenge-1", expiresAt: "2026-08-09T10:05:00Z" },
        });

        await expect(
            login({ username: "admin", password: "secret", powToken: "pow-token-1" }),
        ).resolves.toEqual({
            status: 202,
            challenge: { challengeToken: "challenge-1", expiresAt: "2026-08-09T10:05:00Z" },
        });
    });

    it("submits TOTP login verification as JSON", async () => {
        httpClientMock.post.mockResolvedValue({ sessionId: "session-1" });

        await verifyTotpLoginChallenge({ challengeToken: "challenge/1", code: "123456" });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/login-session-challenges:verifyTotp",
            { challengeToken: "challenge/1", code: "123456" },
        );
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

        expect(httpClientMock.post).toHaveBeenNthCalledWith(1, "/api/v1/totp-enrollments");
        expect(httpClientMock.post).toHaveBeenNthCalledWith(2, "/api/v1/totp-credentials", {
            enrollmentToken: "enrollment-1",
            currentPassword: "secret",
            code: "123456",
        });
        expect(httpClientMock.post).toHaveBeenNthCalledWith(3, "/api/v1/totp-credentials:disable", {
            currentPassword: "secret",
            code: "654321",
        });
    });

    it("resets login failure limit by username", async () => {
        httpClientMock.post.mockResolvedValue(undefined);

        await resetLoginFailureLimit("admin@example.com");

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/login-failure-limits/admin%40example.com:reset",
        );
    });
});
