import { cleanup, render, screen } from "@testing-library/react";
import {
    resetSessionStore,
    useSessionStore,
} from "@archive-management/frontend-core/authentication";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { App } from "./App";

const currentUser = {
    sessionId: "session-1",
    username: "admin",
    displayName: "系统管理员",
    roles: ["admin"],
};

beforeEach(() => {
    stubCurrentUserSession(currentUser);
    window.location.hash = "#/";
});

afterEach(() => {
    cleanup();
    resetSessionStore();
});

describe("移动端门户", () => {
    it("使用真实后端会话渲染移动门户入口", async () => {
        render(<App />);

        expect(await screen.findByText("移动门户")).toBeTruthy();
        expect(screen.getByText("系统管理员")).toBeTruthy();
        expect(screen.getByText("我的待办")).toBeTruthy();
        expect(screen.getByText("归档接收")).toBeTruthy();
        expect(screen.getByText("档案查询")).toBeTruthy();
    });

    it("移动门户不把空显示名称回退为登录名", async () => {
        stubCurrentUserSession({ ...currentUser, displayName: "" });

        render(<App />);

        expect(await screen.findByText("移动门户")).toBeTruthy();
        expect(screen.queryByText("admin")).toBeNull();
    });

    it("未登录时跳转到移动端登录页", async () => {
        stubUnauthenticatedSession();
        window.location.hash = "#/approval/tasks";

        render(<App />);

        expect(await screen.findByRole("heading", { name: "档案移动门户" })).toBeTruthy();
        expect(window.location.hash).toContain("/login");
        expect(window.location.hash).toContain("approval%2Ftasks");
    });
});

function stubCurrentUserSession(user: typeof currentUser) {
    useSessionStore.setState({
        currentUser: null,
        fetchCurrentUser: vi.fn(async () => {
            useSessionStore.setState({
                currentUser: user,
                initialized: true,
            });
        }),
        initialized: false,
    });
}

function stubUnauthenticatedSession() {
    useSessionStore.setState({
        currentUser: null,
        fetchCurrentUser: vi.fn(async () => {
            useSessionStore.setState({
                currentUser: null,
                initialized: true,
            });
        }),
        initialized: false,
    });
}
