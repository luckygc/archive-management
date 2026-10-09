import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import ArchiveRulesPage from "./ArchiveRulesPage.vue";

const apiMocks = vi.hoisted(() => ({
    createArchiveRuntimeDefinition: vi.fn(),
    deleteArchiveRuntimeDefinition: vi.fn(),
    disableArchiveRuntimeDefinition: vi.fn(),
    enableArchiveRuntimeDefinition: vi.fn(),
    getArchiveRuntimeFields: vi.fn(),
    listArchiveRuntimeDefinitions: vi.fn(),
    publishArchiveRuntimeDefinition: vi.fn(),
    simulateArchiveRuntimeDefinitions: vi.fn(),
    updateArchiveRuntimeDefinition: vi.fn(),
}));

vi.mock("@/shared/api/archive-rules", () => apiMocks);

beforeEach(() => {
    apiMocks.listArchiveRuntimeDefinitions.mockResolvedValue({ items: [] });
    apiMocks.getArchiveRuntimeFields.mockResolvedValue({
        categoryCode: "CONTRACT",
        triggerPoint: "ITEM_BEFORE_CREATE",
        signature: "field-signature",
        fields: [
            {
                fieldCode: "metadata.title",
                fieldName: "题名",
                dataType: "TEXT",
                source: "METADATA",
                readable: true,
                writable: true,
                categoryCode: "CONTRACT",
            },
        ],
    });
    apiMocks.simulateArchiveRuntimeDefinitions.mockResolvedValue({
        candidateFacts: { "item.archiveNo": "A-001" },
        assignments: {},
        decisions: [
            {
                definitionCode: "archive-no-block",
                definitionKind: "CONSTRAINT",
                matched: false,
                actions: [],
                message: "档号不允许",
                severity: "ERROR",
                blocking: true,
            },
        ],
        warnings: [],
        blocking: true,
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("运行时规则工作区", () => {
    it("直接加载空状态并按触发点加载真实字段目录", async () => {
        renderPage();

        await waitFor(() =>
            expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalledWith(undefined),
        );

        await fireEvent.click(screen.getByRole("button", { name: "新建定义" }));

        expect(await screen.findByText("真实字段目录")).toBeInTheDocument();
        await waitFor(() => expect(apiMocks.getArchiveRuntimeFields).toHaveBeenCalled());
        expect(await screen.findByText("题名")).toBeInTheDocument();
        expect(screen.getByText("metadata.title")).toBeInTheDocument();
    });

    it("试运行展示阻断决策且不会调用保存接口", async () => {
        renderPage();
        await waitFor(() => expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalled());

        await fireEvent.click(screen.getByRole("button", { name: "试运行" }));
        await fireEvent.click(screen.getByRole("button", { name: "开始试运行" }));

        expect(await screen.findByText("将阻断")).toBeInTheDocument();
        expect(apiMocks.simulateArchiveRuntimeDefinitions).toHaveBeenCalledOnce();
        expect(apiMocks.createArchiveRuntimeDefinition).not.toHaveBeenCalled();
        expect(apiMocks.updateArchiveRuntimeDefinition).not.toHaveBeenCalled();
    });

    it("保存失败保留草稿，重试成功后关闭编辑并刷新列表", async () => {
        apiMocks.createArchiveRuntimeDefinition
            .mockRejectedValueOnce(new Error("服务暂不可用"))
            .mockResolvedValueOnce({ id: 10 });
        renderPage();
        await fireEvent.click(screen.getByRole("button", { name: "新建定义" }));
        const code = await screen.findByPlaceholderText("archive-no-required");
        const name = screen.getByPlaceholderText("档号必填");
        await fireEvent.update(code, "archive-no-required");
        await fireEvent.update(name, "档号必填");
        await fireEvent.click(screen.getByRole("button", { name: "保存草稿" }));

        expect(await screen.findByText("服务暂不可用")).toBeInTheDocument();
        expect(code).toHaveValue("archive-no-required");
        expect(name).toHaveValue("档号必填");
        await fireEvent.click(screen.getByRole("button", { name: "保存草稿" }));

        await waitFor(() =>
            expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalledTimes(2),
        );
        expect(apiMocks.createArchiveRuntimeDefinition).toHaveBeenLastCalledWith(
            expect.objectContaining({
                definitionCode: "archive-no-required",
                definitionName: "档号必填",
                conditionJson: { field: "item.archiveNo", operator: "IS_EMPTY" },
            }),
        );
        await waitFor(() => expect(screen.queryByText("服务暂不可用")).not.toBeInTheDocument());
    });

    it("重新打开试运行时清空上一次结果", async () => {
        renderPage();
        await fireEvent.click(screen.getByRole("button", { name: "试运行" }));
        await fireEvent.click(screen.getByRole("button", { name: "开始试运行" }));
        expect(await screen.findByText("将阻断")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "关闭" }));
        await fireEvent.click(screen.getByRole("button", { name: "试运行" }));

        await waitFor(() => expect(screen.queryByText("将阻断")).not.toBeInTheDocument());
        expect(apiMocks.simulateArchiveRuntimeDefinitions).toHaveBeenCalledOnce();
    });
});

function renderPage() {
    return render(ArchiveRulesPage, { global: { plugins: [ElementPlus] } });
}
