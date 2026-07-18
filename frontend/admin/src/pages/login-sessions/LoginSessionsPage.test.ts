import { listLoginSessions } from "@archive-management/frontend-core/api";
import { cleanup, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, describe, expect, it, vi } from "vitest";

import LoginSessionsPage from "./LoginSessionsPage.vue";

vi.mock("@archive-management/frontend-core/api", () => ({
    deleteLoginSession: vi.fn(),
    listLoginSessions: vi.fn(),
}));

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("LoginSessionsPage", () => {
    it("用户列不把空显示名称回退为账号", async () => {
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
        render(LoginSessionsPage, { global: { plugins: [ElementPlus] } });
        await waitFor(() => expect(screen.getAllByText("zhangsan")).toHaveLength(1));
    });
});
