import { cleanup, fireEvent, render, screen } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, describe, expect, it } from "vitest";

import CursorPagination, { CURSOR_PAGE_SIZE_OPTIONS } from "./CursorPagination.vue";

afterEach(cleanup);

describe("CursorPagination", () => {
    it("使用不透明游标执行前后翻页", async () => {
        const { emitted } = render(CursorPagination, {
            props: { limit: 100, prev: "prev-token", next: "next-token" },
            global: { plugins: [ElementPlus] },
        });

        await fireEvent.click(screen.getByRole("button", { name: "上一页" }));
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));

        expect(emitted().page).toEqual([["prev-token"], ["next-token"]]);
    });

    it("禁用不存在的翻页方向和加载中的控件", async () => {
        const view = render(CursorPagination, {
            props: { limit: 100 },
            global: { plugins: [ElementPlus] },
        });
        expect(screen.getByRole("button", { name: "上一页" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "下一页" })).toBeDisabled();

        await view.rerender({ limit: 100, prev: "prev-token", next: "next-token", loading: true });
        expect(screen.getByRole("button", { name: "上一页" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "下一页" })).toBeDisabled();
        expect(view.container.querySelector(".el-select__wrapper")).toHaveClass("is-disabled");
    });

    it("提供约定的游标分页条数", () => {
        expect(CURSOR_PAGE_SIZE_OPTIONS).toEqual([
            { label: "100 条", value: 100 },
            { label: "200 条", value: 200 },
            { label: "500 条", value: 500 },
            { label: "1000 条", value: 1000 },
        ]);
    });
});
