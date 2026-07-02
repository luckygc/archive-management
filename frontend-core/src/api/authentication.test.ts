import { describe, expect, it, vi } from "vite-plus/test";

import { getCurrentUser, login } from "./authentication";

const httpClientMock = vi.hoisted(() => ({
    delete: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
}));

vi.mock("./client", () => ({
    httpClient: httpClientMock,
}));

describe("authentication API", () => {
    it("loads current user from the canonical session endpoint", async () => {
        httpClientMock.get.mockResolvedValue({
            sessionId: "session-1",
            username: "admin",
            displayName: "系统管理员",
            roles: ["admin"],
        });

        await getCurrentUser();

        expect(httpClientMock.get).toHaveBeenCalledWith("/api/v1/me");
    });

    it("submits password login as a form request with the CAP token", async () => {
        httpClientMock.post.mockResolvedValue({
            sessionId: "session-1",
            username: "admin",
            displayName: "系统管理员",
            roles: ["admin"],
        });

        await login({
            username: "admin",
            password: "secret",
            powToken: "pow-token-1",
        });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/login-sessions",
            expect.any(URLSearchParams),
            {
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                },
            },
        );
        const body = httpClientMock.post.mock.calls[0]?.[1] as URLSearchParams;
        expect(body.get("username")).toBe("admin");
        expect(body.get("password")).toBe("secret");
        expect(body.get("powToken")).toBe("pow-token-1");
    });
});
