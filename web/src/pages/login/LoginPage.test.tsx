import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { resetSessionStore } from "@archive-management/frontend-core/auth";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { AppProviders } from "@/app/AppProviders";
import { resetPageTabsStore } from "@/shared/tabs/pageTabsStore";

import { LoginPage } from "./LoginPage";

const navigate = vi.fn();
const locationState = {
    pathname: "/login",
    search: "?redirect=%2Farchive%2Flibrary",
    hash: "",
};

vi.mock("react-router", async (importOriginal) => {
    const actual = await importOriginal<typeof import("react-router")>();
    return {
        ...actual,
        useLocation: () => locationState,
        useNavigate: () => navigate,
    };
});

beforeEach(() => {
    navigate.mockReset();
    vi.stubGlobal(
        "fetch",
        vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
            if (requestUrl(input) === "/api/v1/login-sessions") {
                expect(init?.method).toBe("POST");
                expect(init?.body).toBeInstanceOf(URLSearchParams);
                const body = init?.body as URLSearchParams;
                expect(body.get("username")).toBe("admin");
                expect(body.get("password")).toBe("secret");
                expect(body.get("powToken")).toBe("pow-token-1");
                return jsonResponse({
                    sessionId: "session-1",
                    username: "admin",
                    displayName: "系统管理员",
                    roles: ["admin"],
                    current: true,
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
                });
            }
            return jsonResponse({}, 404);
        }),
    );
});

afterEach(() => {
    cleanup();
    resetPageTabsStore();
    resetSessionStore();
    vi.unstubAllGlobals();
});

describe("LoginPage", () => {
    it("submits backend login with CAP token and returns to redirect target", async () => {
        render(
            <AppProviders>
                <LoginPage />
            </AppProviders>,
        );

        fireEvent.change(screen.getByLabelText("账号"), { target: { value: "admin" } });
        fireEvent.change(screen.getByLabelText("密码"), { target: { value: "secret" } });
        fireEvent(
            screen.getByTestId("cap-widget"),
            new CustomEvent("solve", {
                bubbles: true,
                detail: {
                    token: "pow-token-1",
                },
            }),
        );

        fireEvent.click(screen.getByRole("button", { name: "登录系统" }));

        await waitFor(() => {
            expect(navigate).toHaveBeenCalledWith("/archive/library", { replace: true });
        });
    });
});

function requestUrl(input: RequestInfo | URL) {
    return input instanceof Request ? input.url : input.toString();
}

function jsonResponse(body: unknown, status = 200) {
    return Promise.resolve(
        new Response(JSON.stringify(body), {
            status,
            headers: { "Content-Type": "application/json" },
        }),
    );
}
