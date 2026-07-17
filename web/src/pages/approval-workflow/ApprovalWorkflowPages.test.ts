import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import ApprovalCenterPage from "./ApprovalCenterPage.vue";
import ApprovalWorkflowDefinitionsPage from "./ApprovalWorkflowDefinitionsPage.vue";

const approvalApiMocks = vi.hoisted(() => ({
    approveApprovalWorkflowTask: vi.fn(),
    getApprovalWorkflowInstance: vi.fn(),
    listApprovalWorkflowDefinitionOptions: vi.fn(),
    listApprovalWorkflowDefinitions: vi.fn(),
    listMyApprovalWorkflowInstances: vi.fn(),
    publishApprovalWorkflowDefinition: vi.fn(),
    rejectApprovalWorkflowTask: vi.fn(),
    setApprovalWorkflowDefinitionEnabled: vi.fn(),
    startApprovalWorkflowInstance: vi.fn(),
    withdrawApprovalWorkflowInstance: vi.fn(),
}));
const unifiedTodoApiMocks = vi.hoisted(() => ({ listMyUnifiedTodos: vi.fn() }));

vi.mock("@/shared/api/approval-workflow", () => approvalApiMocks);
vi.mock("@/shared/api/unified-todo", () => unifiedTodoApiMocks);

beforeEach(() => {
    approvalApiMocks.listApprovalWorkflowDefinitions.mockResolvedValue({
        items: [
            {
                id: 1,
                definitionCode: "contract_approval",
                definitionName: "合同审批",
                businessType: "contract",
                enabled: true,
                draftRevision: 1,
                publishedVersionId: 10,
                graph: { nodes: [
                    {
                        nodeCode: "review",
                        nodeName: "审核",
                        nodeType: "APPROVAL",
                        x: 100,
                        y: 100,
                    },
                ], edges: [] },
                createdAt: "2026-07-17T01:00:00",
                updatedAt: "2026-07-17T01:00:00",
            },
        ],
    });
    unifiedTodoApiMocks.listMyUnifiedTodos.mockResolvedValue({
        items: [
            {
                id: 2,
                sourceType: "APPROVAL_WORKFLOW",
                sourceTaskId: "task-1",
                title: "合同 C-1 审批",
                businessType: "contract",
                businessId: "C-1",
                nodeName: "审核",
                status: "PENDING",
                assigneeUserId: 20,
                sourcePath: "/approval/center?instanceId=3",
                createdAt: "2026-07-17T01:00:00",
            },
        ],
    });
    approvalApiMocks.listMyApprovalWorkflowInstances.mockResolvedValue({ items: [] });
    approvalApiMocks.approveApprovalWorkflowTask.mockResolvedValue({});
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("审批工作页面", () => {
    it("定义管理展示版本状态和草稿操作", async () => {
        renderPage(ApprovalWorkflowDefinitionsPage);

        expect(await screen.findByText("合同审批")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "设计" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "发布" })).toBeInTheDocument();
    });

    it("审批中心展示待办并提交同意意见", async () => {
        renderPage(ApprovalCenterPage);

        expect(await screen.findByText("合同 C-1 审批")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "同意" }));
        await fireEvent.update(screen.getByRole("textbox", { name: "审批意见" }), "同意办理");
        await fireEvent.click(screen.getByRole("button", { name: "确认同意" }));

        await waitFor(() =>
            expect(approvalApiMocks.approveApprovalWorkflowTask).toHaveBeenCalledWith(
                2,
                "同意办理",
            ),
        );
    });
});

function renderPage(component: typeof ApprovalCenterPage | typeof ApprovalWorkflowDefinitionsPage) {
    return render(component, { global: { plugins: [createPinia(), ElementPlus] } });
}
