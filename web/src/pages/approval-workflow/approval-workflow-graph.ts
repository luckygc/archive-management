import type {
    ApprovalFlowEdgeDto,
    ApprovalFlowNodeDto,
    ApprovalNodeType,
    ApprovalWorkflowGraphDto,
} from "@/shared/types/approval-workflow";

export interface LogicFlowNodeData {
    id: string;
    type: string;
    x: number;
    y: number;
    text?: string | { value?: string };
    properties?: Record<string, unknown>;
}

export interface LogicFlowEdgeData {
    id?: string;
    type?: string;
    sourceNodeId: string;
    targetNodeId: string;
    properties?: Record<string, unknown>;
}

export interface LogicFlowGraphData {
    nodes: LogicFlowNodeData[];
    edges: LogicFlowEdgeData[];
}

export interface GraphIssue {
    elementId?: string;
    message: string;
}

const nodeTypeMap: Record<ApprovalNodeType, string> = {
    START: "approval-start",
    APPROVAL: "approval-task",
    EXCLUSIVE_GATEWAY: "approval-gateway",
    END: "approval-end",
};

const reverseNodeTypeMap = Object.fromEntries(
    Object.entries(nodeTypeMap).map(([businessType, logicFlowType]) => [
        logicFlowType,
        businessType,
    ]),
) as Record<string, ApprovalNodeType>;

export function createDefaultApprovalGraph(): ApprovalWorkflowGraphDto {
    return {
        nodes: [
            node("start", "开始", "START", 160, 260),
            node("approval_1", "审批节点", "APPROVAL", 420, 260),
            node("end", "结束", "END", 680, 260),
        ],
        edges: [edge("flow_start_approval_1", "start", "approval_1"), edge("flow_approval_1_end", "approval_1", "end")],
    };
}

export function toLogicFlowGraph(graph: ApprovalWorkflowGraphDto): LogicFlowGraphData {
    return {
        nodes: graph.nodes.map((item) => ({
            id: item.nodeCode,
            type: nodeTypeMap[item.nodeType],
            x: item.x,
            y: item.y,
            text: item.nodeName,
            properties: { ...item, candidateUserIds: [...item.candidateUserIds] },
        })),
        edges: graph.edges.map((item) => ({
            id: item.edgeCode,
            type: "polyline",
            sourceNodeId: item.sourceNodeCode,
            targetNodeId: item.targetNodeCode,
            properties: {
                edgeCode: item.edgeCode,
                defaultFlow: item.defaultFlow,
                condition: item.condition
                    ? { ...item.condition, values: [...item.condition.values] }
                    : undefined,
            },
        })),
    };
}

export function fromLogicFlowGraph(data: LogicFlowGraphData): ApprovalWorkflowGraphDto {
    const usedEdgeCodes = new Set<string>();
    return {
        nodes: data.nodes.map((item) => {
            const properties = item.properties ?? {};
            const nodeType = reverseNodeTypeMap[item.type] ?? (properties.nodeType as ApprovalNodeType);
            return {
                nodeCode: item.id,
                nodeName: textValue(item.text) || String(properties.nodeName ?? item.id),
                nodeType,
                x: Math.round(item.x),
                y: Math.round(item.y),
                candidateStrategy:
                    nodeType === "APPROVAL" ? "SPECIFIED_USERS" : undefined,
                candidateUserIds:
                    nodeType === "APPROVAL"
                        ? numberArray(properties.candidateUserIds)
                        : [],
                allowedActions:
                    nodeType === "APPROVAL" ? ["APPROVE", "REJECT"] : [],
            };
        }),
        edges: data.edges.map((item, index) => {
            const properties = item.properties ?? {};
            const preferred = String(properties.edgeCode ?? item.id ?? `flow_${index + 1}`);
            const edgeCode = uniqueCode(preferred, usedEdgeCodes);
            const rawCondition = properties.condition as ApprovalFlowEdgeDto["condition"];
            return {
                edgeCode,
                sourceNodeCode: item.sourceNodeId,
                targetNodeCode: item.targetNodeId,
                defaultFlow: properties.defaultFlow === true,
                condition: rawCondition
                    ? {
                          field: String(rawCondition.field ?? ""),
                          operator: rawCondition.operator ?? "EQUALS",
                          values: Array.isArray(rawCondition.values)
                              ? rawCondition.values.map(String)
                              : [],
                      }
                    : undefined,
            };
        }),
    };
}

export function validateApprovalGraph(graph: ApprovalWorkflowGraphDto): GraphIssue[] {
    const issues: GraphIssue[] = [];
    const starts = graph.nodes.filter((item) => item.nodeType === "START");
    const ends = graph.nodes.filter((item) => item.nodeType === "END");
    if (starts.length !== 1) issues.push({ message: "流程必须且只能包含一个开始节点" });
    if (ends.length !== 1) issues.push({ message: "流程必须且只能包含一个结束节点" });

    const nodeCodes = new Set(graph.nodes.map((item) => item.nodeCode));
    for (const item of graph.nodes) {
        if (!item.nodeName.trim()) issues.push({ elementId: item.nodeCode, message: "节点名称不能为空" });
        if (item.nodeType === "APPROVAL" && item.candidateUserIds.length === 0) {
            issues.push({ elementId: item.nodeCode, message: `${item.nodeName}：请选择至少一名候选用户` });
        }
        const incoming = graph.edges.filter((edgeItem) => edgeItem.targetNodeCode === item.nodeCode);
        const outgoing = graph.edges.filter((edgeItem) => edgeItem.sourceNodeCode === item.nodeCode);
        if (item.nodeType !== "START" && incoming.length === 0)
            issues.push({ elementId: item.nodeCode, message: `${item.nodeName}：缺少入线` });
        if (item.nodeType !== "END" && outgoing.length === 0)
            issues.push({ elementId: item.nodeCode, message: `${item.nodeName}：缺少出线` });
        if (item.nodeType === "START" && (incoming.length > 0 || outgoing.length !== 1))
            issues.push({ elementId: item.nodeCode, message: "开始节点必须没有入线且只有一条出线" });
        if (item.nodeType === "END" && outgoing.length > 0)
            issues.push({ elementId: item.nodeCode, message: "结束节点不能有出线" });
        if (item.nodeType === "APPROVAL" && outgoing.length !== 1)
            issues.push({ elementId: item.nodeCode, message: `${item.nodeName}：必须且只能有一条出线` });
        if (item.nodeType === "EXCLUSIVE_GATEWAY") {
            const defaults = outgoing.filter((edgeItem) => edgeItem.defaultFlow);
            if (outgoing.length < 2)
                issues.push({ elementId: item.nodeCode, message: `${item.nodeName}：至少需要两个分支` });
            if (defaults.length !== 1)
                issues.push({ elementId: item.nodeCode, message: `${item.nodeName}：必须设置一个默认分支` });
        }
    }

    for (const item of graph.edges) {
        if (!nodeCodes.has(item.sourceNodeCode) || !nodeCodes.has(item.targetNodeCode)) {
            issues.push({ elementId: item.edgeCode, message: "连线引用了不存在的节点" });
        }
        const source = graph.nodes.find((nodeItem) => nodeItem.nodeCode === item.sourceNodeCode);
        if (source?.nodeType === "EXCLUSIVE_GATEWAY" && !item.defaultFlow) {
            if (
                !item.condition?.field.trim() ||
                !/^[a-z][a-z0-9_]{0,63}$/.test(item.condition.field) ||
                item.condition.values.length === 0
            ) {
                issues.push({ elementId: item.edgeCode, message: "条件分支需要填写字段和值" });
            } else if (item.condition.operator !== "IN" && item.condition.values.length !== 1) {
                issues.push({ elementId: item.edgeCode, message: "等于和不等于条件只能填写一个值" });
            }
        }
    }
    if (starts[0]) {
        const reachable = new Set<string>();
        const visiting = new Set<string>();
        let cyclic = false;
        const visit = (nodeCode: string) => {
            if (visiting.has(nodeCode)) {
                cyclic = true;
                return;
            }
            if (reachable.has(nodeCode)) return;
            visiting.add(nodeCode);
            reachable.add(nodeCode);
            graph.edges
                .filter((item) => item.sourceNodeCode === nodeCode)
                .forEach((item) => visit(item.targetNodeCode));
            visiting.delete(nodeCode);
        };
        visit(starts[0].nodeCode);
        for (const item of graph.nodes.filter((nodeItem) => !reachable.has(nodeItem.nodeCode))) {
            issues.push({ elementId: item.nodeCode, message: `${item.nodeName}：无法从开始节点到达` });
        }
        if (cyclic) issues.push({ message: "审批流程不能包含环路" });
    }
    if (ends[0]) {
        const reachesEnd = new Set<string>();
        const visitIncoming = (nodeCode: string) => {
            if (reachesEnd.has(nodeCode)) return;
            reachesEnd.add(nodeCode);
            graph.edges
                .filter((item) => item.targetNodeCode === nodeCode)
                .forEach((item) => visitIncoming(item.sourceNodeCode));
        };
        visitIncoming(ends[0].nodeCode);
        for (const item of graph.nodes.filter((nodeItem) => !reachesEnd.has(nodeItem.nodeCode))) {
            issues.push({ elementId: item.nodeCode, message: `${item.nodeName}：无法到达结束节点` });
        }
    }
    return issues;
}

export function nextNodeCode(graph: ApprovalWorkflowGraphDto, prefix: string) {
    const used = new Set(graph.nodes.map((item) => item.nodeCode));
    let index = 1;
    while (used.has(`${prefix}_${index}`)) index += 1;
    return `${prefix}_${index}`;
}

function node(
    nodeCode: string,
    nodeName: string,
    nodeType: ApprovalNodeType,
    x: number,
    y: number,
): ApprovalFlowNodeDto {
    return {
        nodeCode,
        nodeName,
        nodeType,
        x,
        y,
        candidateStrategy: nodeType === "APPROVAL" ? "SPECIFIED_USERS" : undefined,
        candidateUserIds: [],
        allowedActions: nodeType === "APPROVAL" ? ["APPROVE", "REJECT"] : [],
    };
}

function edge(edgeCode: string, sourceNodeCode: string, targetNodeCode: string): ApprovalFlowEdgeDto {
    return { edgeCode, sourceNodeCode, targetNodeCode, defaultFlow: false };
}

function textValue(value: LogicFlowNodeData["text"]) {
    return typeof value === "string" ? value : value?.value ?? "";
}

function numberArray(value: unknown): number[] {
    return Array.isArray(value)
        ? value.filter((item): item is number => typeof item === "number")
        : [];
}

function uniqueCode(preferred: string, used: Set<string>) {
    let base = preferred.toLowerCase().replace(/[^a-z0-9_-]/g, "_") || "flow";
    if (!/^[a-z]/.test(base)) base = `flow_${base}`;
    base = base.slice(0, 92);
    let code = base;
    let suffix = 2;
    while (used.has(code)) code = `${base}_${suffix++}`;
    used.add(code);
    return code;
}
