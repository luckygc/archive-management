import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus, { ElMessageBox } from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import ArchiveRulesPage from "./ArchiveRulesPage.vue";

const apiMocks = vi.hoisted(() => ({
    createArchiveRuntimeDefinition: vi.fn(),
    deleteArchiveRuntimeDefinition: vi.fn(),
    disableArchiveRuntimeDefinition: vi.fn(),
    enableArchiveRuntimeDefinition: vi.fn(),
    exportArchiveRuntimeSnapshot: vi.fn(),
    getArchiveRuntimeFields: vi.fn(),
    importArchiveRuntimeSnapshot: vi.fn(),
    listArchiveRuntimeDefinitions: vi.fn(),
    preflightArchiveRuntimeSnapshot: vi.fn(),
    publishArchiveRuntimeDefinition: vi.fn(),
    restoreArchiveRuntimeSnapshot: vi.fn(),
    simulateArchiveRuntimeDefinitions: vi.fn(),
    updateArchiveRuntimeDefinition: vi.fn(),
}));

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => ({ query: { schemeVersionId: "11" } }),
}));
vi.mock("@/shared/api/archive-rules", () => apiMocks);

beforeEach(() => {
    apiMocks.listArchiveRuntimeDefinitions.mockResolvedValue({ items: [] });
    apiMocks.getArchiveRuntimeFields.mockResolvedValue({
        schemeVersionId: 11,
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
    apiMocks.preflightArchiveRuntimeSnapshot.mockResolvedValue({
        compatible: true,
        targetSchemeCode: "default-governance",
        definitionCount: 2,
        scopeCount: 1,
        fieldMappings: [
            {
                definitionCode: "title-required",
                sourceCategoryCode: "CONTRACT",
                targetCategoryCode: "CONTRACT",
                sourceFieldCode: "metadata.title",
                targetFieldCode: "metadata.title",
                dataType: "TEXT",
            },
        ],
        sha256: "a".repeat(64),
    });
    apiMocks.importArchiveRuntimeSnapshot.mockResolvedValue({
        schemeVersionId: 23,
        versionCode: "runtime-v2",
        definitionCount: 2,
        scopeCount: 1,
        sha256: "a".repeat(64),
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("运行时规则工作区", () => {
    it("从路由治理版本加载空状态并按触发点加载真实字段目录", async () => {
        renderPage();

        await waitFor(() =>
            expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalledWith(11, undefined),
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

    it("快照预检展示摘要和稳定字段映射", async () => {
        renderPage();
        await waitFor(() => expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalled());

        await fireEvent.click(screen.getByRole("button", { name: "迁移与恢复" }));
        await fireEvent.update(screen.getByLabelText("快照 JSON"), '{"schemaVersion":"1"}');
        await fireEvent.click(screen.getByRole("button", { name: "完整预检" }));

        expect(await screen.findByText("预检通过")).toBeInTheDocument();
        expect(screen.getByText("2 条定义")).toBeInTheDocument();
        expect(screen.getByText("1 个字段引用")).toBeInTheDocument();
        expect(apiMocks.preflightArchiveRuntimeSnapshot).toHaveBeenCalledWith(
            expect.objectContaining({ snapshot: { schemaVersion: "1" } }),
        );
    });

    it("摘要预检失败时原位展示错误且禁止导入", async () => {
        apiMocks.preflightArchiveRuntimeSnapshot.mockRejectedValueOnce(
            new Error("快照摘要校验失败"),
        );
        renderPage();
        await waitFor(() => expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalled());

        await fireEvent.click(screen.getByRole("button", { name: "迁移与恢复" }));
        await fireEvent.update(screen.getByLabelText("快照 JSON"), '{"schemaVersion":"1"}');
        await fireEvent.click(screen.getByRole("button", { name: "完整预检" }));

        expect(await screen.findByText("快照摘要校验失败")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "导入为新草稿" })).toBeDisabled();
        expect(apiMocks.importArchiveRuntimeSnapshot).not.toHaveBeenCalled();
    });

    it("恢复失败保留旧草稿并展示替换摘要", async () => {
        vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
        apiMocks.restoreArchiveRuntimeSnapshot.mockRejectedValueOnce(new Error("恢复写入失败"));
        renderPage();
        await waitFor(() => expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalled());

        await fireEvent.click(screen.getByRole("button", { name: "迁移与恢复" }));
        await fireEvent.update(screen.getByLabelText("快照 JSON"), '{"schemaVersion":"1"}');
        await fireEvent.click(screen.getByRole("button", { name: "完整预检" }));
        await screen.findByText("预检通过");
        const loadCount = apiMocks.listArchiveRuntimeDefinitions.mock.calls.length;
        await fireEvent.click(screen.getByRole("button", { name: "恢复当前草稿" }));

        expect(await screen.findByText("恢复写入失败")).toBeInTheDocument();
        expect(ElMessageBox.confirm).toHaveBeenCalledWith(
            expect.stringContaining("2 条定义全量替换当前草稿"),
            "确认恢复草稿",
            expect.any(Object),
        );
        expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalledTimes(loadCount);
    });

    it("导入成功后打开新建的目标草稿", async () => {
        renderPage();
        await waitFor(() => expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalled());

        await fireEvent.click(screen.getByRole("button", { name: "迁移与恢复" }));
        await fireEvent.update(screen.getByLabelText("快照 JSON"), '{"schemaVersion":"1"}');
        await fireEvent.update(screen.getByLabelText("新草稿版本编码"), "runtime-v2");
        await fireEvent.click(screen.getByRole("button", { name: "完整预检" }));
        await screen.findByText("预检通过");
        await fireEvent.click(screen.getByRole("button", { name: "导入为新草稿" }));

        await waitFor(() =>
            expect(apiMocks.listArchiveRuntimeDefinitions).toHaveBeenCalledWith(23, undefined),
        );
        expect(apiMocks.importArchiveRuntimeSnapshot).toHaveBeenCalledWith(
            expect.objectContaining({ targetVersionCode: "runtime-v2" }),
        );
    });
});

function renderPage() {
    return render(ArchiveRulesPage, { global: { plugins: [ElementPlus] } });
}
