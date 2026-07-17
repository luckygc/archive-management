import { describe, expect, it } from "vite-plus/test";

import {
    createDefaultApprovalGraph,
    fromLogicFlowGraph,
    toLogicFlowGraph,
    validateApprovalGraph,
} from "./approval-workflow-graph";

describe("审批流程设计器图数据", () => {
    it("默认图自动包含唯一开始、审批和结束节点", () => {
        const graph = createDefaultApprovalGraph();

        expect(graph.nodes.map((item) => item.nodeType)).toEqual(["START", "APPROVAL", "END"]);
        expect(graph.edges).toHaveLength(2);
        expect(validateApprovalGraph(graph)).toContainEqual({
            elementId: "approval_1",
            message: "审批节点：请选择至少一名候选用户",
        });
    });

    it("LogicFlow 数据往返时保留布局、候选人和条件", () => {
        const graph = createDefaultApprovalGraph();
        graph.nodes[1]!.candidateUserIds = [7, 8];
        graph.nodes.splice(2, 0, {
            nodeCode: "gateway_1",
            nodeName: "金额判断",
            nodeType: "EXCLUSIVE_GATEWAY",
            x: 560,
            y: 260,
            candidateUserIds: [],
            allowedActions: [],
        });
        graph.edges = [
            graph.edges[0]!,
            {
                edgeCode: "to_gateway",
                sourceNodeCode: "approval_1",
                targetNodeCode: "gateway_1",
                defaultFlow: false,
            },
            {
                edgeCode: "conditional",
                sourceNodeCode: "gateway_1",
                targetNodeCode: "end",
                defaultFlow: false,
                condition: { field: "amount", operator: "IN", values: ["100", "200"] },
            },
            {
                edgeCode: "default",
                sourceNodeCode: "gateway_1",
                targetNodeCode: "approval_1",
                defaultFlow: true,
            },
        ];

        expect(fromLogicFlowGraph(toLogicFlowGraph(graph))).toEqual(graph);
    });

    it("发布检查识别断路和缺少默认分支", () => {
        const graph = createDefaultApprovalGraph();
        graph.nodes[1]!.candidateUserIds = [7];
        graph.nodes.splice(2, 0, {
            nodeCode: "gateway_1",
            nodeName: "条件分支",
            nodeType: "EXCLUSIVE_GATEWAY",
            x: 560,
            y: 260,
            candidateUserIds: [],
            allowedActions: [],
        });
        graph.edges = graph.edges.slice(0, 1);

        expect(validateApprovalGraph(graph).map((item) => item.message)).toEqual(
            expect.arrayContaining([
                "审批节点：缺少出线",
                "条件分支：缺少入线",
                "条件分支：至少需要两个分支",
                "条件分支：必须设置一个默认分支",
                "结束：缺少入线",
            ]),
        );
    });
});
