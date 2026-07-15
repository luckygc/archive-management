import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { defineComponent, h, ref } from "vue";

import { HttpClientError } from "@archive-management/frontend-core/api";
import DashboardPage from "./DashboardPage.vue";

const mocks = vi.hoisted(() => ({ getWorkspaceSummary: vi.fn() }));

vi.mock("@/shared/api/workspace", () => ({
    getWorkspaceSummary: mocks.getWorkspaceSummary,
}));

beforeEach(() => {
    mocks.getWorkspaceSummary.mockResolvedValue({
        archiveItemCount: 12,
        draftCount: 3,
        lockedCount: 2,
        electronicFileCount: 7,
    });
});

afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.clearAllMocks();
});

describe("DashboardPage", () => {
    it("展示服务端四项摘要且移除硬编码统计与虚构事项", async () => {
        renderPage();

        expect(await screen.findByText("12")).toBeVisible();
        expect(screen.getByText("档案总数")).toBeVisible();
        expect(screen.getByText("草稿档案")).toBeVisible();
        expect(screen.getByText("已锁定档案")).toBeVisible();
        expect(screen.getByText("电子文件")).toBeVisible();
        expect(screen.getByText("3")).toBeVisible();
        expect(screen.getByText("2")).toBeVisible();
        expect(screen.getByText("7")).toBeVisible();
        expect(screen.queryByText("财务凭证归档批次待确认")).not.toBeInTheDocument();
        expect(screen.queryByText("近期事项")).not.toBeInTheDocument();
        expect(screen.queryByText("待接收批次")).not.toBeInTheDocument();
    });

    it("加载期间展示骨架而不是虚构零值", async () => {
        const request = deferred<WorkspaceSummary>();
        mocks.getWorkspaceSummary.mockReturnValue(request.promise);
        renderPage();

        expect(await screen.findByLabelText("正在加载档案摘要")).toBeVisible();
        expect(screen.queryByText("档案总数")).not.toBeInTheDocument();

        request.resolve(summary());
        expect(await screen.findByText("档案总数")).toBeVisible();
    });

    it("失败在摘要区域原位重试且不阻断壳层导航，重复点击只发一个请求", async () => {
        const retryRequest = deferred<WorkspaceSummary>();
        mocks.getWorkspaceSummary
            .mockRejectedValueOnce(new Error("摘要服务暂不可用"))
            .mockReturnValueOnce(retryRequest.promise);
        const ShellHarness = defineComponent({
            data: () => ({ navigated: false }),
            render() {
                return h("main", [
                    h("button", { onClick: () => (this.navigated = true) }, "切换工作区"),
                    this.navigated ? h("span", "导航正常") : null,
                    h(DashboardPage),
                ]);
            },
        });
        render(ShellHarness, { global: { plugins: [ElementPlus] } });

        expect(await screen.findByText("摘要服务暂不可用")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "切换工作区" }));
        expect(screen.getByText("导航正常")).toBeVisible();

        const retry = screen.getByRole("button", { name: "重试加载档案摘要" });
        await fireEvent.click(retry);
        await fireEvent.click(retry);
        expect(mocks.getWorkspaceSummary).toHaveBeenCalledTimes(2);

        retryRequest.resolve(summary());
        expect(await screen.findByText("档案总数")).toBeVisible();
    });

    it("重复加载严格返回同一个在途 Promise", async () => {
        const request = deferred<WorkspaceSummary>();
        mocks.getWorkspaceSummary.mockReturnValue(request.promise);
        const page = ref<{ loadSummary: () => Promise<void> }>();
        render(defineComponent({ setup: () => () => h(DashboardPage, { ref: page }) }), {
            global: { plugins: [ElementPlus] },
        });
        await waitFor(() => expect(mocks.getWorkspaceSummary).toHaveBeenCalledTimes(1));

        const first = page.value!.loadSummary();
        const second = page.value!.loadSummary();

        expect(first).toBe(second);
        expect(mocks.getWorkspaceSummary).toHaveBeenCalledTimes(1);
        request.resolve(summary());
        await first;
    });

    it("刷新失败时保留已有摘要并展示追踪 ID，成功后清除错误", async () => {
        mocks.getWorkspaceSummary
            .mockResolvedValueOnce(summary())
            .mockRejectedValueOnce(
                new HttpClientError("摘要刷新失败", 500, "INTERNAL", [], "trace-dashboard"),
            )
            .mockResolvedValueOnce({ ...summary(), archiveItemCount: 13 });
        renderPage();
        expect(await screen.findByText("12")).toBeVisible();

        await fireEvent.click(screen.getByRole("button", { name: "刷新摘要" }));

        expect(await screen.findByText("摘要刷新失败（追踪 ID：trace-dashboard）")).toBeVisible();
        expect(screen.getByText("档案总数")).toBeVisible();
        expect(screen.getByText("12")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "重试加载档案摘要" }));

        expect(await screen.findByText("13")).toBeVisible();
        expect(screen.queryByText(/trace-dashboard/)).not.toBeInTheDocument();
    });

    it("卸载后到达的响应不会继续更新页面或产生警告", async () => {
        const request = deferred<WorkspaceSummary>();
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
        mocks.getWorkspaceSummary.mockReturnValue(request.promise);
        const view = renderPage();

        view.unmount();
        request.resolve(summary());
        await request.promise;
        await waitFor(() => expect(consoleError).not.toHaveBeenCalled());
    });
});

interface WorkspaceSummary {
    archiveItemCount: number;
    draftCount: number;
    lockedCount: number;
    electronicFileCount: number;
}

function summary(): WorkspaceSummary {
    return {
        archiveItemCount: 12,
        draftCount: 3,
        lockedCount: 2,
        electronicFileCount: 7,
    };
}

function renderPage() {
    return render(DashboardPage, { global: { plugins: [ElementPlus] } });
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
}
