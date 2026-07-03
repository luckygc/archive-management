import { listLoginSessions } from "@archive-management/frontend-core/api";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vite-plus/test";

import { LoginSessionsPage } from "./LoginSessionsPage";

vi.mock("@archive-management/frontend-core/api", () => ({
    deleteLoginSession: vi.fn(),
    listLoginSessions: vi.fn(),
}));

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("LoginSessionsPage", () => {
    it("登录会话用户列不把空显示名称回退为账号", async () => {
        vi.mocked(listLoginSessions).mockResolvedValue({
            items: [
                {
                    sessionId: "session-1",
                    username: "zhangsan",
                    displayName: "",
                    roles: [],
                    current: false,
                    client: {
                        userAgent: "",
                        browserName: "",
                        browserVersion: "",
                        osName: "",
                        osVersion: "",
                        deviceType: "",
                    },
                    request: {
                        remoteAddress: "",
                        host: "",
                        forwarded: "",
                        xForwardedFor: "",
                        xRealIp: "",
                    },
                },
            ],
        });

        render(
            <QueryClientProvider client={new QueryClient()}>
                <LoginSessionsPage />
            </QueryClientProvider>,
        );

        await waitFor(() => {
            expect(screen.getAllByText("zhangsan")).toHaveLength(1);
        });
    });
});
