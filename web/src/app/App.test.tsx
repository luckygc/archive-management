import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { resetSessionStore } from "@archive-management/frontend-core/auth";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { resetPageTabsStore } from "@/shared/tabs/pageTabsStore";

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
    resetPageTabsStore();
    resetSessionStore();
    vi.unstubAllGlobals();
});

describe("React 主前端壳层", () => {
    it("renders the Ant Design Pro style archive workspace", async () => {
        render(<App />);

        expect(await screen.findByRole("heading", { name: "工作台" })).toBeTruthy();
        expect(screen.getByText("系统管理员")).toBeTruthy();
        expect(screen.getByText("React / AntD 6")).toBeTruthy();
        expect(screen.getByText("档案管理系统")).toBeTruthy();
        expect(screen.getByRole("link", { name: "档案库" })).toBeTruthy();
        expect(screen.getByRole("menuitem", { name: /目录配置/ })).toBeTruthy();
        expect(screen.getByRole("menuitem", { name: /系统配置/ })).toBeTruthy();
    });

    it("keeps archive route state when switching page tabs", async () => {
        render(<App />);

        fireEvent.click(await screen.findByRole("link", { name: "档案库" }));
        expect(await screen.findByRole("heading", { name: "档案库" })).toBeTruthy();

        fireEvent.change(screen.getByLabelText("当前列表备注"), {
            target: { value: "这个档案库页签状态需要保留" },
        });

        fireEvent.click(screen.getByRole("link", { name: "全宗管理" }));
        expect(await screen.findByRole("heading", { name: "全宗管理" })).toBeTruthy();

        fireEvent.click(screen.getByRole("tab", { name: /档案库/ }));
        await waitFor(() => {
            expect(screen.getByLabelText("当前列表备注")).toHaveProperty(
                "value",
                "这个档案库页签状态需要保留",
            );
        });
    });

    it("refreshes the active page tab by remounting its cache", async () => {
        render(<App />);

        fireEvent.click(await screen.findByRole("link", { name: "档案库" }));
        expect(await screen.findByRole("heading", { name: "档案库" })).toBeTruthy();
        fireEvent.change(screen.getByLabelText("当前列表备注"), {
            target: { value: "刷新后应该清空" },
        });

        fireEvent.click(screen.getByRole("button", { name: "刷新当前页签" }));

        await waitFor(() => {
            expect(screen.getByLabelText("当前列表备注")).toHaveProperty("value", "");
        });
    });

    it("closes a page tab and destroys its cached state", async () => {
        render(<App />);

        fireEvent.click(await screen.findByRole("link", { name: "档案库" }));
        expect(await screen.findByRole("heading", { name: "档案库" })).toBeTruthy();
        fireEvent.change(screen.getByLabelText("当前列表备注"), {
            target: { value: "关闭后应该销毁" },
        });

        fireEvent.click(screen.getByRole("tab", { name: "工作台" }));
        fireEvent.click(screen.getByLabelText("关闭页签：档案库"));
        expect(await screen.findByRole("heading", { name: "工作台" })).toBeTruthy();

        fireEvent.click(screen.getByRole("link", { name: "档案库" }));
        await waitFor(() => {
            expect(screen.getByLabelText("当前列表备注")).toHaveProperty("value", "");
        });
    });

    it("redirects protected routes to login when backend session is unauthenticated", async () => {
        vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
            if (requestUrl(input) === "/api/v1/auth/session") {
                return jsonResponse({ detail: "未登录" }, 401);
            }
            return jsonResponse({}, 404);
        });
        window.location.hash = "#/archive/library";

        render(<App />);

        expect(await screen.findByRole("heading", { name: "账号登录" })).toBeTruthy();
        expect(window.location.hash).toContain("/login");
        expect(window.location.hash).toContain("redirect=");
        expect(window.location.hash).toContain("archive%2Flibrary");
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
