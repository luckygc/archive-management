import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import {
    resetSessionStore,
    useSessionStore,
} from "@archive-management/frontend-core/authentication";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { resetPageTabsStore } from "@/shared/tabs/pageTabsStore";

import { App } from "./App";

const archiveApiMocks = vi.hoisted(() => ({
    discoverArchiveRecords: vi.fn(),
    listArchiveCategories: vi.fn(),
    listArchiveFields: vi.fn(),
    listArchiveRelatedFilterCategories: vi.fn(),
}));

vi.mock("@/shared/api/archive", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@/shared/api/archive")>();
    return {
        ...actual,
        ...archiveApiMocks,
    };
});

const currentUser = {
    sessionId: "session-1",
    username: "admin",
    displayName: "系统管理员",
    roles: ["admin"],
};

beforeEach(() => {
    useSessionStore.setState({
        currentUser,
        initialized: true,
    });
    archiveApiMocks.listArchiveCategories.mockResolvedValue({
        items: [
            {
                id: 1,
                categoryCode: "GW",
                categoryName: "公文档案",
                enabled: true,
                managementMode: "ITEM_ONLY",
                sortOrder: 10,
                tableStatus: "BUILT",
            },
        ],
    });
    archiveApiMocks.listArchiveFields.mockResolvedValue({ items: [] });
    archiveApiMocks.listArchiveRelatedFilterCategories.mockResolvedValue({ items: [] });
    archiveApiMocks.discoverArchiveRecords.mockResolvedValue({
        fields: [],
        items: [],
        tableBuilt: true,
    });
    window.location.hash = "#/";
});

afterEach(() => {
    cleanup();
    resetPageTabsStore();
    resetSessionStore();
});

describe("React 主前端壳层", () => {
    it("renders the Ant Design Pro style archive workspace", async () => {
        render(<App />);

        expect(await screen.findByRole("heading", { name: "工作台" })).toBeTruthy();
        expect(screen.getByText("系统管理员")).toBeTruthy();
        expect(screen.getByText("档案管理系统")).toBeTruthy();
        expect(screen.getByRole("link", { name: "档案搜索" })).toBeTruthy();
        expect(screen.getByRole("link", { name: "档案管理" })).toBeTruthy();
        expect(screen.getByRole("menuitem", { name: /目录配置/ })).toBeTruthy();
        expect(screen.getByRole("menuitem", { name: /系统配置/ })).toBeTruthy();
    });

    it("keeps archive route state when switching page tabs", async () => {
        render(<App />);

        fireEvent.click(await screen.findByRole("link", { name: "档案搜索" }));
        expect(await screen.findByRole("heading", { name: "档案搜索" })).toBeTruthy();

        fireEvent.change(screen.getByLabelText("全文关键词"), {
            target: { value: "这个查询草稿需要保留" },
        });

        fireEvent.click(screen.getByRole("link", { name: "全宗管理" }));
        expect(await screen.findByRole("heading", { name: "全宗管理" })).toBeTruthy();

        fireEvent.click(screen.getByRole("tab", { name: /档案搜索/ }));
        await waitFor(() => {
            expect(screen.getByLabelText("全文关键词")).toHaveProperty(
                "value",
                "这个查询草稿需要保留",
            );
        });
    });

    it("refreshes the active page tab by remounting its cache", async () => {
        render(<App />);

        fireEvent.click(await screen.findByRole("link", { name: "档案搜索" }));
        expect(await screen.findByRole("heading", { name: "档案搜索" })).toBeTruthy();
        fireEvent.change(screen.getByLabelText("全文关键词"), {
            target: { value: "刷新后应该清空" },
        });

        fireEvent.click(screen.getByRole("button", { name: "刷新当前页签" }));

        await waitFor(() => {
            expect(screen.getByLabelText("全文关键词")).toHaveProperty("value", "");
        });
    });

    it("closes a page tab and destroys its cached state", async () => {
        render(<App />);

        fireEvent.click(await screen.findByRole("link", { name: "档案搜索" }));
        expect(await screen.findByRole("heading", { name: "档案搜索" })).toBeTruthy();
        fireEvent.change(screen.getByLabelText("全文关键词"), {
            target: { value: "关闭后应该销毁" },
        });

        fireEvent.click(screen.getByRole("tab", { name: "工作台" }));
        fireEvent.click(screen.getByLabelText("关闭页签：档案搜索"));
        expect(await screen.findByRole("heading", { name: "工作台" })).toBeTruthy();

        fireEvent.click(screen.getByRole("link", { name: "档案搜索" }));
        await waitFor(() => {
            expect(screen.getByLabelText("全文关键词")).toHaveProperty("value", "");
        });
    });

    it("redirects protected routes to login when backend session is unauthenticated", async () => {
        useSessionStore.setState({
            currentUser: null,
            initialized: true,
        });
        window.location.hash = "#/archive/library";

        render(<App />);

        expect(await screen.findByRole("heading", { name: "账号登录" })).toBeTruthy();
        expect(window.location.hash).toContain("/login");
        expect(window.location.hash).toContain("redirect=");
        expect(window.location.hash).toContain("archive%2Flibrary");
    });
});
