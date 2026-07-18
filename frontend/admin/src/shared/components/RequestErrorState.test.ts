import { cleanup, fireEvent, render, screen } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, describe, expect, it } from "vitest";

import RequestErrorState from "./RequestErrorState.vue";

afterEach(cleanup);

describe("RequestErrorState", () => {
    it("展示错误原因并使用默认标签发出重试事件", async () => {
        const view = render(RequestErrorState, {
            props: { message: "查询失败（追踪 ID：trace-11）" },
            global: { plugins: [ElementPlus] },
        });

        expect(screen.getByText("查询失败（追踪 ID：trace-11）")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));

        expect(view.emitted("retry")).toEqual([[]]);
    });

    it("支持自定义标签", () => {
        render(RequestErrorState, {
            props: { message: "加载失败", retryLabel: "重新加载摘要" },
            global: { plugins: [ElementPlus] },
        });

        expect(screen.getByRole("button", { name: "重新加载摘要" })).toBeEnabled();
    });

    it("重试中展示加载状态并禁用重复操作", async () => {
        const view = render(RequestErrorState, {
            props: { message: "加载失败", retrying: true },
            global: { plugins: [ElementPlus] },
        });
        const retry = screen.getByRole("button", { name: "重试" });

        expect(retry).toBeDisabled();
        expect(retry).toHaveClass("is-loading");
        await fireEvent.click(retry);

        expect(view.emitted("retry")).toBeUndefined();
    });

    it("显式禁用时不发出重试事件", async () => {
        const view = render(RequestErrorState, {
            props: { message: "加载失败", disabled: true },
            global: { plugins: [ElementPlus] },
        });
        const retry = screen.getByRole("button", { name: "重试" });

        expect(retry).toBeDisabled();
        await fireEvent.click(retry);
        expect(view.emitted("retry")).toBeUndefined();
    });
});
