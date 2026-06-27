import { cleanup, render, screen } from "@testing-library/react";
import { resetSessionStore } from "@archive-management/app-core/auth";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { App } from "./App";

const currentUser = {
    username: "admin",
    displayName: "系统管理员",
    roles: ["admin"],
};

beforeEach(() => {
    vi.stubGlobal(
        "fetch",
        vi.fn(async (input: RequestInfo | URL) => {
            if (requestUrl(input) === "/api/v1/auth/session") {
                return jsonResponse(currentUser);
            }
            return jsonResponse({}, 404);
        }),
    );
    window.location.hash = "#/";
});

afterEach(() => {
    cleanup();
    resetSessionStore();
    vi.unstubAllGlobals();
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

    it("未登录时跳转到移动端登录页", async () => {
        vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
            if (requestUrl(input) === "/api/v1/auth/session") {
                return jsonResponse({ detail: "未登录" }, 401);
            }
            return jsonResponse({}, 404);
        });
        window.location.hash = "#/approval/tasks";

        render(<App />);

        expect(await screen.findByRole("heading", { name: "档案移动门户" })).toBeTruthy();
        expect(window.location.hash).toContain("/login");
        expect(window.location.hash).toContain("approval%2Ftasks");
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
