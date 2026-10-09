import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ArchiveRuleEditorDialog from "./ArchiveRuleEditorDialog.vue";

const mocks = vi.hoisted(() => ({
    createArchiveRuntimeDefinition: vi.fn(),
    getArchiveRuntimeFields: vi.fn(),
    updateArchiveRuntimeDefinition: vi.fn(),
}));
vi.mock("@/shared/api/archive-rules", () => mocks);

beforeEach(() => {
    vi.resetAllMocks();
    mocks.getArchiveRuntimeFields.mockResolvedValue(catalog("初始字段"));
});
afterEach(cleanup);

describe("运行时定义字段目录", () => {
    it("分类切换后旧目录响应不覆盖当前分类", async () => {
        const oldRequest = deferred<ReturnType<typeof catalog>>();
        const currentRequest = deferred<ReturnType<typeof catalog>>();
        renderEditor();
        await openEditor();
        mocks.getArchiveRuntimeFields
            .mockReturnValueOnce(oldRequest.promise)
            .mockReturnValueOnce(currentRequest.promise);
        const category = screen.getByPlaceholderText("为空表示全部分类");
        await fireEvent.update(category, "A");
        await fireEvent.update(category, "B");
        await waitFor(() => expect(mocks.getArchiveRuntimeFields).toHaveBeenCalledTimes(3));

        currentRequest.resolve(catalog("当前分类字段"));
        expect(await screen.findByText("当前分类字段")).toBeVisible();
        oldRequest.resolve(catalog("旧分类字段"));
        await oldRequest.promise;
        await flushAsync();

        expect(screen.queryByText("旧分类字段")).not.toBeInTheDocument();
        expect(screen.getByText("当前分类字段")).toBeVisible();
    });

    it("旧分类请求失败不清空当前目录或展示旧错误", async () => {
        const oldRequest = deferred<ReturnType<typeof catalog>>();
        renderEditor();
        await openEditor();
        mocks.getArchiveRuntimeFields
            .mockReturnValueOnce(oldRequest.promise)
            .mockResolvedValueOnce(catalog("当前分类字段"));
        const category = screen.getByPlaceholderText("为空表示全部分类");
        await fireEvent.update(category, "A");
        await fireEvent.update(category, "B");
        expect(await screen.findByText("当前分类字段")).toBeVisible();

        oldRequest.reject(new Error("旧分类失败"));
        await oldRequest.promise.catch(() => undefined);
        await flushAsync();

        expect(screen.getByText("当前分类字段")).toBeVisible();
        expect(screen.queryByText("旧分类失败")).not.toBeInTheDocument();
    });

    it("关闭重开后同作用域的旧响应仍被丢弃", async () => {
        const oldRequest = deferred<ReturnType<typeof catalog>>();
        mocks.getArchiveRuntimeFields.mockReturnValueOnce(oldRequest.promise);
        renderEditor();
        await fireEvent.click(screen.getByRole("button", { name: "打开编辑" }));
        await waitFor(() => expect(mocks.getArchiveRuntimeFields).toHaveBeenCalledOnce());
        await fireEvent.click(screen.getByRole("button", { name: "取消" }));
        await fireEvent.click(screen.getByRole("button", { name: "打开编辑" }));
        expect(await screen.findByText("初始字段")).toBeVisible();

        oldRequest.resolve(catalog("上次打开字段"));
        await oldRequest.promise;
        await flushAsync();

        expect(screen.queryByText("上次打开字段")).not.toBeInTheDocument();
        expect(screen.getByText("初始字段")).toBeVisible();
    });

    it("目录失败持久显示并可重试，成功后保留独立的保存错误和草稿", async () => {
        const retry = deferred<ReturnType<typeof catalog>>();
        mocks.getArchiveRuntimeFields
            .mockRejectedValueOnce(new Error("字段目录不可用"))
            .mockReturnValueOnce(retry.promise);
        mocks.createArchiveRuntimeDefinition.mockRejectedValueOnce(new Error("草稿保存失败"));
        renderEditor();
        await fireEvent.click(screen.getByRole("button", { name: "打开编辑" }));
        expect(await screen.findByText("字段目录不可用")).toBeVisible();
        await fireEvent.update(screen.getByPlaceholderText("archive-no-required"), "RULE-001");
        await fireEvent.update(screen.getByPlaceholderText("档号必填"), "保留草稿");
        await fireEvent.click(screen.getByRole("button", { name: "保存草稿" }));
        expect(await screen.findByText("草稿保存失败")).toBeVisible();
        expect(screen.getByText("字段目录不可用")).toBeVisible();
        const retryButton = screen.getByRole("button", { name: "重试字段目录" });
        await fireEvent.click(retryButton);
        expect(retryButton).toBeDisabled();
        expect(screen.getByText("字段目录不可用")).toBeVisible();
        retry.resolve(catalog("重试字段"));
        expect(await screen.findByText("重试字段")).toBeVisible();
        expect(screen.queryByText("字段目录不可用")).not.toBeInTheDocument();
        expect(screen.getByText("草稿保存失败")).toBeVisible();
        expect(screen.getByPlaceholderText("档号必填")).toHaveValue("保留草稿");
    });
});

function renderEditor() {
    return render(
        defineComponent({
            components: { ArchiveRuleEditorDialog },
            setup: () => ({ open: ref(false) }),
            template: `<button @click="open = true">打开编辑</button><ArchiveRuleEditorDialog v-model="open" />`,
        }),
        { global: { plugins: [ElementPlus] } },
    );
}

async function openEditor() {
    await fireEvent.click(screen.getByRole("button", { name: "打开编辑" }));
    await screen.findByText("初始字段");
}

function catalog(fieldName: string) {
    return {
        fields: [{ fieldCode: "metadata.title", fieldName, dataType: "TEXT", writable: true }],
    };
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
