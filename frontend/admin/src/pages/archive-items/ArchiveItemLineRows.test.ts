import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { HttpClientError } from "@archive-management/frontend-core/api";

import type { ArchiveFieldType } from "@/shared/types/archive-metadata";

import ArchiveItemLineRows from "./ArchiveItemLineRows.vue";

const mocks = vi.hoisted(() => ({
    create: vi.fn(),
    confirm: vi.fn(),
    delete: vi.fn(),
    listRows: vi.fn(),
    listItemTables: vi.fn(),
    patch: vi.fn(),
}));

vi.mock("@/shared/api/archive-line-tables", () => ({
    createArchiveItemLineRow: mocks.create,
    deleteArchiveItemLineRow: mocks.delete,
    listArchiveItemLineRows: mocks.listRows,
    listArchiveItemLineTables: mocks.listItemTables,
    patchArchiveItemLineRow: mocks.patch,
}));

vi.mock("element-plus", async (importOriginal) => {
    const actual = await importOriginal<typeof import("element-plus")>();
    return {
        ...actual,
        ElMessage: { error: vi.fn(), success: vi.fn() },
        ElMessageBox: { confirm: mocks.confirm },
    };
});

describe("档案明细行", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mocks.listItemTables.mockResolvedValue({ items: [lineTable] });
        mocks.listRows.mockResolvedValue({ items: [row], next: "next-token" });
        mocks.create.mockResolvedValue({ ...row, id: 10 });
        mocks.patch.mockResolvedValue({ ...row, values: { ...row.values, remark: null } });
        mocks.delete.mockResolvedValue(undefined);
        mocks.confirm.mockResolvedValue(undefined);
    });
    afterEach(cleanup);

    it("创建态没有 itemId 时不加载或展示明细", async () => {
        renderRows({ archiveItemId: undefined });

        expect(screen.queryByText("合同方")).not.toBeInTheDocument();
        expect(mocks.listItemTables).not.toHaveBeenCalled();
        expect(mocks.listRows).not.toHaveBeenCalled();
    });

    it("详情与锁定状态只读但仍加载行", async () => {
        renderRows({ readonly: true });

        expect(await screen.findByText("甲公司")).toBeInTheDocument();
        expect(mocks.listRows).toHaveBeenCalledWith(3, 4, { limit: 100, cursor: undefined });
        expect(screen.queryByRole("button", { name: "新增明细" })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "编辑" })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "删除" })).not.toBeInTheDocument();
    });

    it("新增明细按五类字段定义提交 values 且只刷新当前表", async () => {
        renderRows();
        await screen.findByText("甲公司");

        await fireEvent.click(screen.getByRole("button", { name: "新增明细" }));
        await fireEvent.update(screen.getByLabelText("单位名称"), "乙公司");
        await fireEvent.update(screen.getByLabelText("数量"), "2");
        await fireEvent.update(screen.getByLabelText("金额"), "18.50");
        const dateInput = screen.getByLabelText("日期");
        await fireEvent.focus(dateInput);
        await fireEvent.update(dateInput, "2026-07-15");
        await fireEvent.keyDown(dateInput, { key: "Enter" });
        const dateTimeInput = screen.getByLabelText("时间");
        await fireEvent.focus(dateTimeInput);
        await fireEvent.update(dateTimeInput, "2026-07-15 09:30:00");
        await fireEvent.keyDown(dateTimeInput, { key: "Enter" });
        await fireEvent.click(screen.getByRole("button", { name: "保存明细" }));

        await waitFor(() =>
            expect(mocks.create).toHaveBeenCalledWith(3, 4, {
                lineOrder: 0,
                values: {
                    party_name: "乙公司",
                    count: 2,
                    amount: "18.50",
                    signed_on: "2026-07-15",
                    signed_at: "2026-07-15 09:30:00",
                    remark: null,
                },
            }),
        );
        expect(mocks.listRows).toHaveBeenCalledTimes(2);
        expect(mocks.listItemTables).toHaveBeenCalledTimes(1);
    });

    it("编辑只 PATCH 变化值并保留显式清空", async () => {
        renderRows();
        await screen.findByText("甲公司");

        await fireEvent.click(screen.getByRole("button", { name: "编辑" }));
        await fireEvent.update(screen.getByLabelText("备注"), "");
        await fireEvent.click(screen.getByRole("button", { name: "保存明细" }));

        await waitFor(() =>
            expect(mocks.patch).toHaveBeenCalledWith(3, 4, 9, {
                values: { remark: null },
            }),
        );
    });

    it("列表失败保留原位重试且切换 item 后丢弃旧响应", async () => {
        let resolveOld!: (value: unknown) => void;
        mocks.listRows.mockReturnValueOnce(
            new Promise((resolve) => {
                resolveOld = resolve;
            }),
        );
        const view = renderRows();
        await waitFor(() => expect(mocks.listRows).toHaveBeenCalledOnce());

        await view.rerender({ archiveItemId: 5, readonly: false });
        resolveOld({ items: [{ ...row, values: { ...row.values, party_name: "旧响应" } }] });

        await waitFor(() => expect(mocks.listRows).toHaveBeenCalledTimes(2));
        expect(screen.queryByText("旧响应")).not.toBeInTheDocument();
    });

    it("明细行加载失败后在当前表原位重试", async () => {
        mocks.listRows.mockRejectedValueOnce(new Error("网络暂不可用"));
        renderRows();

        expect(await screen.findByText("网络暂不可用")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试明细行" }));

        expect(await screen.findByText("甲公司")).toBeInTheDocument();
        expect(mocks.listRows).toHaveBeenCalledTimes(2);
    });

    it("明细表定义加载失败后原位重试", async () => {
        mocks.listItemTables.mockRejectedValueOnce(new Error("定义加载失败"));
        renderRows();

        expect(await screen.findByText("定义加载失败")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试明细表" }));

        expect(await screen.findByText("甲公司")).toBeInTheDocument();
        expect(mocks.listItemTables).toHaveBeenCalledTimes(2);
    });

    it("确认删除后调用当前行接口且只刷新当前表", async () => {
        renderRows();
        await screen.findByText("甲公司");

        await fireEvent.click(screen.getByRole("button", { name: "删除" }));

        await waitFor(() => expect(mocks.delete).toHaveBeenCalledWith(3, 4, 9));
        await waitFor(() => expect(mocks.listRows).toHaveBeenCalledTimes(2));
        expect(mocks.listItemTables).toHaveBeenCalledTimes(1);
    });

    it("删除确认期间切为只读后不再发送删除请求", async () => {
        let resolveConfirm!: () => void;
        mocks.confirm.mockReturnValueOnce(
            new Promise<void>((resolve) => {
                resolveConfirm = resolve;
            }),
        );
        const view = renderRows();
        await screen.findByText("甲公司");

        await fireEvent.click(screen.getByRole("button", { name: "删除" }));
        await waitFor(() => expect(mocks.confirm).toHaveBeenCalledOnce());
        await view.rerender({ archiveItemId: 3, readonly: true });
        resolveConfirm();
        await Promise.resolve();
        await Promise.resolve();
        expect(mocks.delete).not.toHaveBeenCalled();
    });

    it("删除请求发出后切为只读仍完成刷新且不残留忙碌状态", async () => {
        let resolveDelete!: () => void;
        mocks.delete.mockReturnValueOnce(
            new Promise<void>((resolve) => {
                resolveDelete = resolve;
            }),
        );
        const view = renderRows();
        await screen.findByText("甲公司");

        await fireEvent.click(screen.getByRole("button", { name: "删除" }));
        await waitFor(() => expect(mocks.delete).toHaveBeenCalledOnce());
        await view.rerender({ archiveItemId: 3, readonly: true });
        resolveDelete();

        await waitFor(() => expect(mocks.listRows).toHaveBeenCalledTimes(2));
        await view.rerender({ archiveItemId: 3, readonly: false });
        expect(screen.getByRole("button", { name: "删除" })).toBeEnabled();
    });

    it("保存命令进行中禁用删除以避免命令交叉并发", async () => {
        let resolvePatch!: (value: unknown) => void;
        mocks.patch.mockReturnValueOnce(
            new Promise((resolve) => {
                resolvePatch = resolve;
            }),
        );
        renderRows();
        await screen.findByText("甲公司");
        await fireEvent.click(screen.getByRole("button", { name: "编辑" }));
        await fireEvent.update(screen.getByLabelText("备注"), "新备注");
        await fireEvent.click(screen.getByRole("button", { name: "保存明细" }));

        await waitFor(() => expect(mocks.patch).toHaveBeenCalledOnce());
        expect(screen.getByRole("button", { name: "删除" })).toBeDisabled();
        resolvePatch(row);
    });

    it("字段校验失败在原表单回填并保留输入供重试", async () => {
        mocks.patch.mockRejectedValueOnce(
            new HttpClientError("字段校验失败", 400, "INVALID_ARGUMENT", [
                { field: "values.remark", message: "备注格式不合法" },
            ]),
        );
        renderRows();
        await screen.findByText("甲公司");
        await fireEvent.click(screen.getByRole("button", { name: "编辑" }));
        await fireEvent.update(screen.getByLabelText("备注"), "待修订");
        await fireEvent.click(screen.getByRole("button", { name: "保存明细" }));

        expect(await screen.findByText("备注格式不合法")).toBeInTheDocument();
        expect(screen.getByLabelText("备注")).toHaveValue("待修订");
        await fireEvent.click(screen.getByRole("button", { name: "保存明细" }));
        await waitFor(() => expect(mocks.patch).toHaveBeenCalledTimes(2));
    });
});

function renderRows(props: { archiveItemId?: number; readonly?: boolean } = {}) {
    return render(ArchiveItemLineRows, {
        props: {
            archiveItemId: 3,
            readonly: false,
            ...props,
        },
        global: { plugins: [ElementPlus], stubs: { TransitionGroup: false } },
    });
}

const lineTable = {
    id: 4,
    tableCode: "contract_party",
    tableName: "合同方",
    sortOrder: 1,
    fields: [
        field("party_name", "单位名称", "TEXT"),
        field("count", "数量", "INTEGER"),
        field("amount", "金额", "DECIMAL"),
        field("signed_on", "日期", "DATE"),
        field("signed_at", "时间", "DATETIME"),
        field("remark", "备注", "TEXT"),
    ],
};

const row = {
    id: 9,
    archiveItemId: 3,
    lineTableId: 4,
    lineOrder: 0,
    values: {
        party_name: "甲公司",
        count: 1,
        amount: "12.50",
        signed_on: "2026-07-14",
        signed_at: "2026-07-14 08:00:00",
        remark: "原备注",
    },
};

function field(fieldCode: string, fieldName: string, fieldType: ArchiveFieldType) {
    return {
        id: fieldCode.length,
        fieldCode,
        fieldName,
        fieldType,
        sortOrder: 1,
    };
}
