import { beforeEach, describe, expect, it, vi } from "vite-plus/test";

import {
    approveApprovalWorkflowTask,
    listApprovalWorkflowDefinitionVersions,
    publishApprovalWorkflowDefinition,
    rejectApprovalWorkflowTask,
    updateApprovalWorkflowDefinition,
    withdrawApprovalWorkflowInstance,
} from "./approval-workflow";

const httpClientMock = vi.hoisted(() => ({ get: vi.fn(), patch: vi.fn(), post: vi.fn() }));

vi.mock("@archive-management/frontend-core/api", () => ({ httpClient: httpClientMock }));

beforeEach(() => {
    vi.clearAllMocks();
});

describe("approval workflow API", () => {
    it("流程定义更新使用 Merge Patch 媒体类型", async () => {
        const graph = { nodes: [], edges: [] };
        await updateApprovalWorkflowDefinition(7, {
            definitionName: "合同审批",
            businessType: "contract",
            graph,
        });

        expect(httpClientMock.patch).toHaveBeenCalledWith(
            "/approval-workflow-definitions/7",
            { definitionName: "合同审批", businessType: "contract", graph },
            { headers: { "Content-Type": "application/merge-patch+json" } },
        );
    });

    it("定义版本列表将游标参数放入 URL query", async () => {
        await listApprovalWorkflowDefinitionVersions(7, { limit: 200, cursor: "next-token" });

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/approval-workflow-definitions/7/versions?limit=200&cursor=next-token",
        );
    });

    it("审批动作使用 AIP 冒号动作路径", async () => {
        await publishApprovalWorkflowDefinition(1);
        await approveApprovalWorkflowTask(2, "同意");
        await rejectApprovalWorkflowTask(3, "材料不完整");
        await withdrawApprovalWorkflowInstance(4, "业务取消");

        expect(httpClientMock.post).toHaveBeenNthCalledWith(
            1,
            "/approval-workflow-definitions/1:publish",
        );
        expect(httpClientMock.post).toHaveBeenNthCalledWith(
            2,
            "/approval-workflow-tasks/2:approve",
            { comment: "同意" },
        );
        expect(httpClientMock.post).toHaveBeenNthCalledWith(
            3,
            "/approval-workflow-tasks/3:reject",
            { comment: "材料不完整" },
        );
        expect(httpClientMock.post).toHaveBeenNthCalledWith(
            4,
            "/approval-workflow-instances/4:withdraw",
            { comment: "业务取消" },
        );
    });
});
