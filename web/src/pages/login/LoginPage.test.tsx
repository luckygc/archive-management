import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import {
    resetSessionStore,
    useSessionStore,
} from "@archive-management/frontend-core/authentication";
import type { LoginRequest } from "@archive-management/frontend-core/types";
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
    useSessionStore.setState({
        loginWithPassword: vi.fn(async (request: LoginRequest) => {
            expect(request).toEqual({
                username: "admin",
                password: "secret",
                powToken: "pow-token-1",
            });
            const currentUser = {
                sessionId: "session-1",
                username: "admin",
                displayName: "系统管理员",
                roles: ["admin"],
            };
            useSessionStore.setState({ currentUser, initialized: true });
            return currentUser;
        }),
    });
});

afterEach(() => {
    cleanup();
    resetPageTabsStore();
    resetSessionStore();
});

describe("LoginPage", () => {
    it("submits backend login with CAP token and returns to redirect target", async () => {
        render(
            <AppProviders>
                <LoginPage />
            </AppProviders>,
        );
        Object.assign(screen.getByTestId("cap-widget"), {
            reset: vi.fn(),
        });

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
