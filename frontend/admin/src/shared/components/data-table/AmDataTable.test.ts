import { cleanup, fireEvent, render, screen, within } from "@testing-library/vue";
import { afterEach, describe, expect, it } from "vitest";
import type { Component } from "vue";

import AmDataTable from "./AmDataTable.vue";
import type { AmDataTableColumn } from "./types";

const TestDataTable = AmDataTable as Component;

type ExampleRow = {
    id: number;
    group: string;
    score: number;
    children?: ExampleRow[];
};

const columns: AmDataTableColumn<ExampleRow>[] = [
    { key: "group", label: "分组", sortable: true },
    { key: "score", label: "分数", sortable: true, align: "right" },
];

afterEach(cleanup);

describe("AmDataTable", () => {
    it("渲染业务单元格、空状态和加载状态", async () => {
        const view = render(TestDataTable, {
            props: {
                columns,
                data: [{ id: 1, group: "甲", score: 90 }],
                rowKey: "id",
            },
            slots: {
                "cell-score": ({ row }: { row: ExampleRow }) => `得分 ${row.score}`,
            },
        });

        expect(screen.getByText("得分 90")).toBeInTheDocument();

        await view.rerender({ columns, data: [], rowKey: "id", emptyText: "暂无结果" });
        expect(screen.getByText("暂无结果")).toBeInTheDocument();

        await view.rerender({ columns, data: [], rowKey: "id", loading: true });
        expect(screen.getByText("正在加载…")).toBeInTheDocument();
        expect(screen.queryByText("暂无结果")).not.toBeInTheDocument();
    });

    it("支持带优先级的本地多列排序", async () => {
        render(TestDataTable, {
            props: {
                columns,
                data: [
                    { id: 1, group: "乙", score: 2 },
                    { id: 2, group: "甲", score: 2 },
                    { id: 3, group: "甲", score: 1 },
                ],
                rowKey: "id",
            },
        });

        await fireEvent.click(screen.getByRole("button", { name: /^分组，未排序/ }));
        await fireEvent.click(screen.getByRole("button", { name: /^分数，未排序/ }), {
            shiftKey: true,
        });

        const rows = screen.getAllByRole("row").slice(1);
        expect(rows.map((row) => within(row).getAllByRole("cell")[1]?.textContent)).toEqual([
            "1",
            "2",
            "2",
        ]);
        expect(screen.getAllByText(/^[12]$/).length).toBeGreaterThanOrEqual(5);
    });

    it("远程排序只发送状态而不重排当前页", async () => {
        const view = render(TestDataTable, {
            props: {
                columns,
                data: [
                    { id: 1, group: "乙", score: 2 },
                    { id: 2, group: "甲", score: 1 },
                ],
                rowKey: "id",
                sortMode: "manual",
            },
        });

        await fireEvent.click(screen.getByRole("button", { name: /^分组，未排序/ }));

        expect(view.emitted().sortingChange).toEqual([[[{ id: "group", desc: false }]]]);
        const rows = screen.getAllByRole("row").slice(1);
        expect(within(rows[0]!).getAllByRole("cell")[0]).toHaveTextContent("乙");
    });

    it("按层级展开和收起子行", async () => {
        render(TestDataTable, {
            props: {
                columns,
                data: [
                    {
                        id: 1,
                        group: "父级",
                        score: 2,
                        children: [{ id: 2, group: "子级", score: 1 }],
                    },
                ],
                rowKey: "id",
                childrenKey: "children",
                sortMode: "none",
            },
        });

        expect(screen.queryByText("子级")).not.toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "展开父级" }));
        expect(screen.getByText("子级")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "收起父级" }));
        expect(screen.queryByText("子级")).not.toBeInTheDocument();
    });

    it("异步收到树数据后保持默认全部展开", async () => {
        const view = render(TestDataTable, {
            props: {
                columns,
                data: [],
                rowKey: "id",
                childrenKey: "children",
                defaultExpandAll: true,
                sortMode: "none",
            },
        });

        await view.rerender({
            columns,
            data: [
                {
                    id: 1,
                    group: "父级",
                    score: 2,
                    children: [{ id: 2, group: "子级", score: 1 }],
                },
            ],
            rowKey: "id",
            childrenKey: "children",
            defaultExpandAll: true,
            sortMode: "none",
        });

        expect(await screen.findByText("子级")).toBeInTheDocument();
    });
});
