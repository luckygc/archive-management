import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus, { ElMessage } from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { HttpClientError } from "@archive-management/frontend-core/api";

import ArchiveLineTablePanel from "./ArchiveLineTablePanel.vue";

const mocks = vi.hoisted(() => ({
    buildArchiveLineTable: vi.fn(),
    createArchiveLineField: vi.fn(),
    createArchiveLineTable: vi.fn(),
    listArchiveLineFields: vi.fn(),
    listArchiveLineTables: vi.fn(),
}));

vi.mock("@/shared/api/archive-line-tables", () => mocks);

beforeEach(() => {
    mocks.listArchiveLineTables.mockResolvedValue({ items: [lineTable()] });
    mocks.listArchiveLineFields.mockResolvedValue({ items: [lineField()] });
    mocks.createArchiveLineTable.mockResolvedValue(lineTable(13, "participant", "参与方"));
    mocks.createArchiveLineField.mockResolvedValue(
        lineField(22, 13, "contact_name", "联系人", "f_contact_name"),
    );
    mocks.buildArchiveLineTable.mockResolvedValue(lineTable());
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ArchiveLineTablePanel", () => {
    it("创建明细表和字段后可显式构建，并阻止各动作重复提交", async () => {
        const createTableRequest = deferred<ReturnType<typeof lineTable>>();
        mocks.createArchiveLineTable.mockReturnValueOnce(createTableRequest.promise);
        renderPanel(7);
        expect(await screen.findByText("合同方")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "配置合同方字段" })).toBeVisible();

        await fireEvent.click(screen.getByRole("button", { name: "新增明细表" }));
        await fireEvent.update(screen.getByLabelText("明细表编码"), "participant");
        await fireEvent.update(screen.getByLabelText("明细表名称"), "参与方");
        const saveTableButton = screen.getByRole("button", { name: "保存明细表" });
        await fireEvent.click(saveTableButton);
        await waitFor(() => expect(mocks.createArchiveLineTable).toHaveBeenCalledTimes(1));
        await fireEvent.click(saveTableButton);
        expect(mocks.createArchiveLineTable).toHaveBeenCalledTimes(1);
        expect(mocks.createArchiveLineTable).toHaveBeenCalledWith(7, {
            tableCode: "participant",
            tableName: "参与方",
            sortOrder: 0,
        });
        createTableRequest.resolve(lineTable(13, "participant", "参与方"));

        expect(await screen.findByText("参与方")).toBeInTheDocument();
        const createFieldRequest = deferred<ReturnType<typeof lineField>>();
        mocks.createArchiveLineField.mockReturnValueOnce(createFieldRequest.promise);
        const createFieldButton = screen.getByRole("button", { name: "新增字段" });
        await waitFor(() => expect(createFieldButton).toBeEnabled());
        await fireEvent.click(createFieldButton);
        await fireEvent.update(screen.getByLabelText("明细字段编码"), "contact_name");
        await fireEvent.update(screen.getByLabelText("明细字段名称"), "联系人");
        await fireEvent.update(screen.getByLabelText("物理列名"), "f_contact_name");
        await fireEvent.click(screen.getByRole("checkbox", { name: "精确检索" }));
        const saveFieldButton = screen.getByRole("button", { name: "保存字段" });
        await fireEvent.click(saveFieldButton);
        await waitFor(() => expect(mocks.createArchiveLineField).toHaveBeenCalledTimes(1));
        await fireEvent.click(saveFieldButton);
        expect(mocks.createArchiveLineField).toHaveBeenCalledTimes(1);
        expect(mocks.createArchiveLineField).toHaveBeenCalledWith(13, {
            fieldCode: "contact_name",
            fieldName: "联系人",
            fieldType: "TEXT",
            columnName: "f_contact_name",
            exactSearchable: true,
            sortOrder: 0,
        });
        createFieldRequest.resolve(lineField(22, 13, "contact_name", "联系人", "f_contact_name"));

        expect(await screen.findByText("联系人")).toBeInTheDocument();
        const buildRequest = deferred<ReturnType<typeof lineTable>>();
        mocks.buildArchiveLineTable.mockReturnValueOnce(buildRequest.promise);
        const buildButton = screen.getByRole("button", { name: "构建数据表" });
        await fireEvent.click(buildButton);
        await waitFor(() => expect(mocks.buildArchiveLineTable).toHaveBeenCalledTimes(1));
        await fireEvent.click(buildButton);
        expect(mocks.buildArchiveLineTable).toHaveBeenCalledTimes(1);
        expect(mocks.buildArchiveLineTable).toHaveBeenCalledWith(13);
        buildRequest.resolve(lineTable(13, "participant", "参与方"));
        await waitFor(() => expect(buildButton).toBeEnabled());
    });

    it("构建失败保留定义并在原位使用同一明细表重试", async () => {
        mocks.buildArchiveLineTable
            .mockRejectedValueOnce(new Error("数据库建表失败"))
            .mockResolvedValueOnce(lineTable());
        renderPanel(7);
        expect(await screen.findByText("单位名称")).toBeInTheDocument();

        await fireEvent.click(screen.getByRole("button", { name: "构建数据表" }));
        expect(await screen.findByText("数据库建表失败")).toBeVisible();
        expect(screen.getByText("合同方")).toBeInTheDocument();
        expect(screen.getByText("单位名称")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试构建" }));

        await waitFor(() => expect(mocks.buildArchiveLineTable).toHaveBeenCalledTimes(2));
        expect(mocks.buildArchiveLineTable).toHaveBeenLastCalledWith(12);
        await waitFor(() => expect(screen.queryByText("数据库建表失败")).not.toBeInTheDocument());
    });

    it("切换分类后丢弃旧列表响应并只加载新分类字段", async () => {
        const oldRequest = deferred<{ items: ReturnType<typeof lineTable>[] }>();
        const currentRequest = deferred<{ items: ReturnType<typeof lineTable>[] }>();
        mocks.listArchiveLineTables
            .mockReturnValueOnce(oldRequest.promise)
            .mockReturnValueOnce(currentRequest.promise);
        const view = renderPanel(7);

        await view.rerender({ categoryId: 8 });
        currentRequest.resolve({ items: [lineTable(18, "invoice", "发票明细")] });
        expect(await screen.findByText("发票明细")).toBeInTheDocument();
        oldRequest.resolve({ items: [lineTable(12, "contract_party", "旧合同方")] });
        await oldRequest.promise;

        await waitFor(() => expect(screen.queryByText("旧合同方")).not.toBeInTheDocument());
        expect(mocks.listArchiveLineFields).toHaveBeenCalledWith(18);
        expect(mocks.listArchiveLineFields).not.toHaveBeenCalledWith(12);
    });

    it("切换明细表后立即失效旧表命令状态和响应", async () => {
        mocks.listArchiveLineTables.mockResolvedValue({
            items: [lineTable(), lineTable(13, "participant", "参与方")],
        });
        mocks.listArchiveLineFields.mockImplementation(async (lineTableId: number) => ({
            items: [
                lineTableId === 12
                    ? lineField()
                    : lineField(22, 13, "contact_name", "联系人", "f_contact_name"),
            ],
        }));
        const oldBuild = deferred<ReturnType<typeof lineTable>>();
        mocks.buildArchiveLineTable.mockReturnValueOnce(oldBuild.promise);
        renderPanel(7);
        expect(await screen.findByText("单位名称")).toBeInTheDocument();

        await fireEvent.click(screen.getByRole("button", { name: "构建数据表" }));
        await waitFor(() => expect(mocks.buildArchiveLineTable).toHaveBeenCalledWith(12));
        await fireEvent.click(screen.getByRole("button", { name: "配置参与方字段" }));

        expect(await screen.findByText("联系人")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "构建数据表" })).toBeEnabled();
        oldBuild.resolve(lineTable());
        await oldBuild.promise;
        expect(screen.getByText("参与方字段")).toBeInTheDocument();
    });

    it("切换明细表后丢弃旧字段列表响应", async () => {
        mocks.listArchiveLineTables.mockResolvedValue({
            items: [lineTable(), lineTable(13, "participant", "参与方")],
        });
        const oldFields = deferred<{ items: ReturnType<typeof lineField>[] }>();
        mocks.listArchiveLineFields.mockReturnValueOnce(oldFields.promise).mockResolvedValueOnce({
            items: [lineField(22, 13, "contact_name", "联系人", "f_contact_name")],
        });
        renderPanel(7);
        await waitFor(() => expect(mocks.listArchiveLineFields).toHaveBeenCalledWith(12));

        await screen.findByText("参与方");
        await fireEvent.click(await screen.findByRole("button", { name: "配置参与方字段" }));
        expect(await screen.findByText("联系人")).toBeInTheDocument();
        oldFields.resolve({ items: [lineField()] });
        await oldFields.promise;

        await waitFor(() => expect(screen.queryByText("单位名称")).not.toBeInTheDocument());
        expect(screen.getByText("联系人")).toBeInTheDocument();
    });

    it("创建明细表期间切换分类时不提示成功也不污染新分类", async () => {
        const success = vi.spyOn(ElMessage, "success");
        const request = deferred<ReturnType<typeof lineTable>>();
        mocks.createArchiveLineTable.mockReturnValueOnce(request.promise);
        const view = renderPanel(7);
        expect(await screen.findByText("合同方")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "新增明细表" }));
        await fireEvent.update(screen.getByLabelText("明细表编码"), "stale_table");
        await fireEvent.update(screen.getByLabelText("明细表名称"), "旧分类明细");
        await fireEvent.click(screen.getByRole("button", { name: "保存明细表" }));
        await waitFor(() => expect(mocks.createArchiveLineTable).toHaveBeenCalledTimes(1));

        mocks.listArchiveLineTables.mockResolvedValueOnce({
            items: [lineTable(18, "invoice", "发票明细")],
        });
        await view.rerender({ categoryId: 8 });
        request.resolve(lineTable(19, "stale_table", "旧分类明细"));
        await request.promise;

        expect(await screen.findByText("发票明细")).toBeInTheDocument();
        expect(screen.queryByText("旧分类明细")).not.toBeInTheDocument();
        expect(success).not.toHaveBeenCalledWith("明细表已创建");
    });

    it("创建字段期间切换明细表或卸载时不提示成功也不污染当前状态", async () => {
        const success = vi.spyOn(ElMessage, "success");
        mocks.listArchiveLineTables.mockResolvedValue({
            items: [lineTable(), lineTable(13, "participant", "参与方")],
        });
        const request = deferred<ReturnType<typeof lineField>>();
        mocks.createArchiveLineField.mockReturnValueOnce(request.promise);
        const view = renderPanel(7);
        expect(await screen.findByText("单位名称")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "新增字段" }));
        await fillFieldForm("stale_field", "旧字段", "f_stale_field");
        await fireEvent.click(screen.getByRole("button", { name: "保存字段" }));
        await waitFor(() => expect(mocks.createArchiveLineField).toHaveBeenCalledTimes(1));

        await fireEvent.click(screen.getByRole("button", { name: "配置参与方字段" }));
        request.resolve(lineField(23, 12, "stale_field", "旧字段", "f_stale_field"));
        await request.promise;

        expect(screen.queryByText("旧字段")).not.toBeInTheDocument();
        expect(success).not.toHaveBeenCalledWith("明细字段已创建");
        view.unmount();
    });

    it("创建明细表期间卸载时丢弃响应且不提示成功", async () => {
        const success = vi.spyOn(ElMessage, "success");
        const request = deferred<ReturnType<typeof lineTable>>();
        mocks.createArchiveLineTable.mockReturnValueOnce(request.promise);
        const view = renderPanel(7);
        expect(await screen.findByText("合同方")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "新增明细表" }));
        await fireEvent.update(screen.getByLabelText("明细表编码"), "stale_table");
        await fireEvent.update(screen.getByLabelText("明细表名称"), "已卸载明细");
        await fireEvent.click(screen.getByRole("button", { name: "保存明细表" }));
        await waitFor(() => expect(mocks.createArchiveLineTable).toHaveBeenCalledTimes(1));

        view.unmount();
        request.resolve(lineTable(19, "stale_table", "已卸载明细"));
        await request.promise;

        expect(success).not.toHaveBeenCalledWith("明细表已创建");
    });

    it("创建字段期间卸载时丢弃响应且不提示成功", async () => {
        const success = vi.spyOn(ElMessage, "success");
        const request = deferred<ReturnType<typeof lineField>>();
        mocks.createArchiveLineField.mockReturnValueOnce(request.promise);
        const view = renderPanel(7);
        expect(await screen.findByText("单位名称")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "新增字段" }));
        await fillFieldForm("stale_field", "已卸载字段", "f_stale_field");
        await fireEvent.click(screen.getByRole("button", { name: "保存字段" }));
        await waitFor(() => expect(mocks.createArchiveLineField).toHaveBeenCalledTimes(1));

        view.unmount();
        request.resolve(lineField(23, 12, "stale_field", "已卸载字段", "f_stale_field"));
        await request.promise;

        expect(success).not.toHaveBeenCalledWith("明细字段已创建");
    });

    it("字段校验错误回填物理列名和精确检索并保留输入", async () => {
        mocks.createArchiveLineField.mockRejectedValueOnce(
            new HttpClientError("字段校验失败", 400, "INVALID_ARGUMENT", [
                { field: "columnName", message: "物理列名已存在" },
                { field: "exactSearchable", message: "当前类型不支持精确检索" },
            ]),
        );
        renderPanel(7);
        expect(await screen.findByText("单位名称")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "新增字段" }));
        await fillFieldForm("contact_name", "联系人", "f_contact_name");
        await fireEvent.click(screen.getByRole("checkbox", { name: "精确检索" }));
        await fireEvent.click(screen.getByRole("button", { name: "保存字段" }));

        expect(await screen.findByText("物理列名已存在")).toBeInTheDocument();
        expect(await screen.findByText("当前类型不支持精确检索")).toBeInTheDocument();
        expect(screen.getByLabelText("物理列名")).toHaveValue("f_contact_name");
    });
});

function renderPanel(categoryId: number) {
    return render(ArchiveLineTablePanel, {
        props: { categoryId },
        global: { plugins: [ElementPlus] },
    });
}

function lineTable(id = 12, tableCode = "contract_party", tableName = "合同方") {
    return {
        id,
        categoryId: 7,
        tableCode,
        tableName,
        physicalTableName: `am_archive_item_line_${tableCode}`,
        sortOrder: 1,
        enabled: true,
        fields: [],
    };
}

function lineField(
    id = 21,
    lineTableId = 12,
    fieldCode = "party_name",
    fieldName = "单位名称",
    columnName = "f_party_name",
) {
    return {
        id,
        lineTableId,
        fieldCode,
        fieldName,
        fieldType: "TEXT" as const,
        columnName,
        exactSearchable: true,
        sortOrder: 1,
        enabled: true,
    };
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, resolve, reject };
}

async function fillFieldForm(fieldCode: string, fieldName: string, columnName: string) {
    await fireEvent.update(screen.getByLabelText("明细字段编码"), fieldCode);
    await fireEvent.update(screen.getByLabelText("明细字段名称"), fieldName);
    await fireEvent.update(screen.getByLabelText("物理列名"), columnName);
}
