import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { HttpClientError } from "@archive-management/frontend-core/api";
import ArchiveFondsPage from "./ArchiveFondsPage.vue";
import ArchiveFondsEventsDrawer from "./ArchiveFondsEventsDrawer.vue";

const mocks = vi.hoisted(() => ({
    assignArchiveFondsNumber: vi.fn(),
    closeArchiveFonds: vi.fn(),
    createArchiveFonds: vi.fn(),
    listArchiveFonds: vi.fn(),
    listArchiveFondsEvents: vi.fn(),
    reopenArchiveFonds: vi.fn(),
    updateArchiveFonds: vi.fn(),
}));
vi.mock("@/shared/api/archive-metadata", () => mocks);

let records: ReturnType<typeof fonds>[];
beforeEach(() => {
    vi.resetAllMocks();
    records = [fonds(1, "ACTIVE"), fonds(2, "CLOSED")];
    mocks.listArchiveFonds.mockImplementation(async (status?: string) => ({
        items: records.filter((row) => !status || row.status === status),
    }));
    mocks.closeArchiveFonds.mockImplementation(async () => {
        records[0] = fonds(1, "CLOSED");
        return records[0];
    });
    mocks.createArchiveFonds.mockImplementation(async () => {
        const created = fonds(3, "ACTIVE");
        records.push(created);
        return created;
    });
    mocks.listArchiveFondsEvents.mockResolvedValue({ items: [] });
});
afterEach(cleanup);

describe("全宗列表和事件恢复", () => {
    it("有效筛选下封闭后按当前筛选刷新，不保留已封闭记录", async () => {
        renderPage();
        await screen.findByText("F001");
        await selectStatus("有效");
        await fireEvent.click(await screen.findByRole("button", { name: "封闭" }));
        await fireEvent.update(screen.getByRole("textbox", { name: "办理原因" }), "机构撤并");
        await fireEvent.click(screen.getByRole("button", { name: "确认封闭" }));

        await waitFor(() => expect(screen.queryByText("F001")).not.toBeInTheDocument());
        expect(mocks.listArchiveFonds).toHaveBeenLastCalledWith("ACTIVE");
        expect(screen.getByText("暂无全宗")).toBeVisible();
    });

    it("已封闭筛选下新建有效全宗后不追加到当前列表", async () => {
        renderPage();
        await screen.findByText("F001");
        await selectStatus("已封闭");
        await fireEvent.click(screen.getByRole("button", { name: "新建全宗" }));
        await fireEvent.update(screen.getByRole("textbox", { name: "系统全宗编码" }), "F003");
        await fireEvent.update(screen.getByRole("textbox", { name: "全宗名称" }), "新机构");
        await fireEvent.click(screen.getByRole("button", { name: "保存" }));
        await waitFor(() => expect(mocks.createArchiveFonds).toHaveBeenCalledOnce());
        await waitFor(() =>
            expect(screen.queryByRole("button", { name: "保存" })).not.toBeInTheDocument(),
        );

        expect(screen.queryByText("F003")).not.toBeInTheDocument();
        expect(screen.getByText("F002")).toBeVisible();
        expect(mocks.listArchiveFonds).toHaveBeenLastCalledWith("CLOSED");
    });

    it("筛选变化后迟到的旧列表不覆盖新筛选", async () => {
        const oldRequest = deferred<{ items: ReturnType<typeof fonds>[] }>();
        mocks.listArchiveFonds.mockReturnValueOnce(oldRequest.promise);
        renderPage();
        await selectStatus("已封闭");
        expect(await screen.findByText("F002")).toBeVisible();
        oldRequest.resolve({ items: [fonds(1, "ACTIVE")] });
        await oldRequest.promise;
        await flushAsync();

        expect(screen.queryByText("F001")).not.toBeInTheDocument();
        expect(screen.getByText("F002")).toBeVisible();
    });

    it("列表加载失败显示带追踪信息的错误并原位重试", async () => {
        const retry = deferred<{ items: ReturnType<typeof fonds>[] }>();
        mocks.listArchiveFonds
            .mockRejectedValueOnce(
                new HttpClientError("全宗服务不可用", 500, "INTERNAL", [], "fonds-trace"),
            )
            .mockReturnValueOnce(retry.promise);
        renderPage();
        expect(await screen.findByText("全宗服务不可用（追踪 ID：fonds-trace）")).toBeVisible();
        expect(screen.queryByText("暂无全宗")).not.toBeInTheDocument();
        const button = screen.getByRole("button", { name: "重试全宗列表" });
        await fireEvent.click(button);
        await fireEvent.click(button);
        expect(mocks.listArchiveFonds).toHaveBeenCalledTimes(2);
        expect(button).toBeDisabled();

        retry.resolve({ items: [fonds(1, "ACTIVE")] });
        expect(await screen.findByText("F001")).toBeVisible();
        expect(
            screen.queryByText("全宗服务不可用（追踪 ID：fonds-trace）"),
        ).not.toBeInTheDocument();
    });

    it("事件翻页失败保留记录并使用失败的分页链接原位重试", async () => {
        const next = "/archive-fonds/1/events?cursor=next&limit=100";
        mocks.listArchiveFondsEvents
            .mockResolvedValueOnce({ items: [event("旧事件")], next })
            .mockRejectedValueOnce(new Error("事件服务不可用"))
            .mockResolvedValueOnce({ items: [event("下一页事件")] });
        renderPage();
        await fireEvent.click((await screen.findAllByRole("button", { name: "事件" }))[0]!);
        expect(await screen.findByText("旧事件")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));
        expect(await screen.findByText("事件服务不可用")).toBeVisible();
        expect(screen.getByText("旧事件")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "重试全宗事件" }));

        expect(await screen.findByText("下一页事件")).toBeVisible();
        expect(mocks.listArchiveFondsEvents).toHaveBeenLastCalledWith(1, 100, next);
        expect(screen.queryByText("事件服务不可用")).not.toBeInTheDocument();
    });

    it("事件游标失效清除旧分页链接，保留记录和追踪信息并从首页重试", async () => {
        const expired = deferred<{ items: ReturnType<typeof event>[] }>();
        const retry = deferred<{ items: ReturnType<typeof event>[] }>();
        mocks.listArchiveFondsEvents
            .mockResolvedValueOnce({
                items: [event("旧事件")],
                self: "/archive-fonds/1/events?cursor=self",
                prev: "/archive-fonds/1/events?cursor=prev",
                next: "/archive-fonds/1/events?cursor=expired",
                first: "/archive-fonds/1/events",
            })
            .mockReturnValueOnce(expired.promise)
            .mockReturnValueOnce(retry.promise);
        renderPage();
        await fireEvent.click((await screen.findAllByRole("button", { name: "事件" }))[0]!);
        expect(await screen.findByText("旧事件")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));
        expired.reject(
            new HttpClientError(
                "游标已过期",
                400,
                "INVALID_ARGUMENT",
                [{ field: "cursor", message: "游标已过期" }],
                "events-cursor-trace",
            ),
        );
        const message = "数据已变化，将从第一页重新加载（追踪 ID：events-cursor-trace）";
        expect(await screen.findByText(message)).toBeVisible();
        expect(screen.getByText("旧事件")).toBeVisible();
        expect(screen.getByRole("button", { name: "上一页" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "下一页" })).toBeDisabled();
        const button = screen.getByRole("button", { name: "重试全宗事件" });
        await fireEvent.click(button);

        expect(mocks.listArchiveFondsEvents).toHaveBeenLastCalledWith(1, 100, undefined);
        expect(button).toBeDisabled();
        expect(screen.getByText(message)).toBeVisible();
        retry.resolve({ items: [event("首页事件")] });
        expect(await screen.findByText("首页事件")).toBeVisible();
        expect(screen.queryByText(message)).not.toBeInTheDocument();
    });

    it("事件抽屉关闭重开后丢弃同全宗的旧响应", async () => {
        const oldRequest = deferred<{ items: ReturnType<typeof event>[] }>();
        mocks.listArchiveFondsEvents
            .mockReturnValueOnce(oldRequest.promise)
            .mockResolvedValueOnce({ items: [event("当前事件")] });
        const target = fonds(1, "ACTIVE");
        const view = render(ArchiveFondsEventsDrawer, {
            props: { modelValue: true, fonds: target },
            global: { plugins: [ElementPlus] },
        });
        await waitFor(() => expect(mocks.listArchiveFondsEvents).toHaveBeenCalledOnce());
        await view.rerender({ modelValue: false, fonds: target });
        await view.rerender({ modelValue: true, fonds: target });
        await waitFor(() => expect(screen.getByText("当前事件")).toBeVisible());
        oldRequest.resolve({ items: [event("旧请求事件")] });
        await oldRequest.promise;
        await flushAsync();

        expect(screen.queryByText("旧请求事件")).not.toBeInTheDocument();
        expect(screen.getByText("当前事件")).toBeVisible();
    });
});

function renderPage() {
    return render(ArchiveFondsPage, { global: { plugins: [ElementPlus] } });
}
async function selectStatus(label: string) {
    await fireEvent.click(screen.getByRole("combobox", { name: "全宗状态筛选" }));
    await fireEvent.click(await screen.findByRole("option", { name: label }));
}
function fonds(id: number, status: "ACTIVE" | "CLOSED") {
    return {
        id,
        fondsCode: `F00${id}`,
        fondsName: `机构${id}`,
        status,
        sortOrder: 0,
        createdAt: "2026-10-09T10:00:00",
        updatedAt: "2026-10-09T10:00:00",
    };
}
function event(reason: string) {
    return { id: 11, eventType: "CLOSED", effectiveAt: "2026-10-09T10:00:00", reason };
}
function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((onResolve, onReject) => {
        resolve = onResolve;
        reject = onReject;
    });
    return { promise, resolve, reject };
}
async function flushAsync() {
    await Promise.resolve();
    await Promise.resolve();
}
