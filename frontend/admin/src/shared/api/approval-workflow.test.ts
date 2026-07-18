import { beforeEach, describe, expect, it, vi } from "vite-plus/test";

import {
    approveApprovalWorkflowTask,
    listApprovalWorkflowDefinitionVersions,
    publishApprovalWorkflowDefinition,
    rejectApprovalWorkflowTask,
    withdrawApprovalWorkflowInstance,
} from "./approval-workflow";

const httpClientMock = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@archive-management/frontend-core/api", () => ({ httpClient: httpClientMock }));

beforeEach(() => {
    vi.clearAllMocks();
});

describe("approval workflow API", () => {
    it("定义版本列表将游标参数放入 URL query", async () => {
        await listApprovalWorkflowDefinitionVersions(7, { limit: 200, cursor: "next-token" });

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/api/v1/approval-workflow-definitions/7/versions?limit=200&cursor=next-token",
        );
    });

    it("审批动作使用 AIP 冒号动作路径", async () => {
        await publishApprovalWorkflowDefinition(1);
        await approveApprovalWorkflowTask(2, "同意");
        await rejectApprovalWorkflowTask(3, "材料不完整");
        await withdrawApprovalWorkflowInstance(4, "业务取消");

        expect(httpClientMock.post).toHaveBeenNthCalledWith(
            1,
            "/api/v1/approval-workflow-definitions/1:publish",
        );
        expect(httpClientMock.post).toHaveBeenNthCalledWith(
            2,
            "/api/v1/approval-workflow-tasks/2:approve",
            { comment: "同意" },
        );
        expect(httpClientMock.post).toHaveBeenNthCalledWith(
            3,
            "/api/v1/approval-workflow-tasks/3:reject",
            { comment: "材料不完整" },
        );
        expect(httpClientMock.post).toHaveBeenNthCalledWith(
            4,
            "/api/v1/approval-workflow-instances/4:withdraw",
            { comment: "业务取消" },
        );
    });
});
