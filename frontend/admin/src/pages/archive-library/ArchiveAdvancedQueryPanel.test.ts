import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, describe, expect, it } from "vite-plus/test";

import ArchiveAdvancedQueryPanel from "./ArchiveAdvancedQueryPanel.vue";
import ArchiveQueryValueInput from "./ArchiveQueryValueInput.vue";

afterEach(cleanup);

describe("ArchiveAdvancedQueryPanel", () => {
    it("未选择分类时阻止提交", async () => {
        const view = render(ArchiveAdvancedQueryPanel, {
            props: {
                modelValue: { conditions: [], relatedGroups: [] },
                categories: [],
                fields: [],
                relatedCategories: [],
                relatedFieldsByCategory: new Map(),
                showKeyword: true,
            },
            global: { plugins: [ElementPlus] },
        });

        await fireEvent.click(screen.getByRole("button", { name: "查询" }));
        await Promise.resolve();

        expect(view.emitted("submit")).toBeUndefined();
    });

    it("添加了未选择字段的条件时阻止提交", async () => {
        const view = render(ArchiveAdvancedQueryPanel, {
            props: {
                modelValue: { categoryId: 1, conditions: [{ op: "EQ" }], relatedGroups: [] },
                categories: [{ id: 1, categoryName: "文书档案" }] as never,
                fields: [],
                relatedCategories: [],
                relatedFieldsByCategory: new Map(),
                showKeyword: true,
            },
            global: { plugins: [ElementPlus] },
        });

        await fireEvent.click(screen.getByRole("button", { name: "查询" }));
        await Promise.resolve();

        expect(view.emitted("submit")).toBeUndefined();
    });

    it("必填项完整时提交查询", async () => {
        const view = render(ArchiveAdvancedQueryPanel, {
            props: {
                modelValue: { categoryId: 1, conditions: [], relatedGroups: [] },
                categories: [{ id: 1, categoryName: "文书档案" }] as never,
                fields: [],
                relatedCategories: [],
                relatedFieldsByCategory: new Map(),
                showKeyword: true,
            },
            global: { plugins: [ElementPlus] },
        });

        await fireEvent.click(screen.getByRole("button", { name: "查询" }));

        await waitFor(() => expect(view.emitted("submit")).toHaveLength(1));
    });
});

describe("ArchiveQueryValueInput", () => {
    it("按照字段类型使用数字与日期输入控件", async () => {
        const view = render(ArchiveQueryValueInput, {
            props: {
                field: { fieldType: "DECIMAL", decimalScale: 3 } as never,
                placeholder: "查询值",
            },
            global: { plugins: [ElementPlus] },
        });
        expect(screen.getByRole("spinbutton")).toBeInTheDocument();

        await view.rerender({
            field: { fieldType: "DATE" } as never,
            placeholder: "查询值",
        });

        await waitFor(() => expect(screen.getByPlaceholderText("查询值")).toBeInTheDocument());
        expect(screen.queryByRole("spinbutton")).not.toBeInTheDocument();
    });
});
