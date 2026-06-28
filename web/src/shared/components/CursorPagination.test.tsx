import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vite-plus/test";

import { CURSOR_PAGE_SIZE_OPTIONS, CursorPagination } from "./CursorPagination";

afterEach(() => {
    cleanup();
});

describe("CursorPagination", () => {
    it("uses opaque cursor tokens for previous and next page actions", () => {
        const onPage = vi.fn();

        render(
            <CursorPagination
                limit={100}
                prev="prev-token"
                next="next-token"
                onLimitChange={vi.fn()}
                onPage={onPage}
            />,
        );

        fireEvent.click(screen.getByRole("button", { name: "上一页" }));
        fireEvent.click(screen.getByRole("button", { name: "下一页" }));

        expect(onPage).toHaveBeenNthCalledWith(1, "prev-token");
        expect(onPage).toHaveBeenNthCalledWith(2, "next-token");
    });

    it("disables unavailable page directions and loading state", () => {
        const { rerender } = render(
            <CursorPagination limit={100} onLimitChange={vi.fn()} onPage={vi.fn()} />,
        );

        expect(screen.getByRole("button", { name: "上一页" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "下一页" })).toBeDisabled();

        rerender(
            <CursorPagination
                limit={100}
                prev="prev-token"
                next="next-token"
                loading
                onLimitChange={vi.fn()}
                onPage={vi.fn()}
            />,
        );

        expect(screen.getByRole("button", { name: "上一页" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "下一页" })).toBeDisabled();
        expect(screen.getByLabelText("每页条数")).toBeDisabled();
    });

    it("offers standard cursor page sizes", () => {
        expect(CURSOR_PAGE_SIZE_OPTIONS).toEqual([
            { label: "100 条", value: 100 },
            { label: "200 条", value: 200 },
            { label: "500 条", value: 500 },
            { label: "1000 条", value: 1000 },
        ]);
    });
});
