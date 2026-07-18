<script setup lang="ts">
import LogicFlow, {
    CircleNode,
    CircleNodeModel,
    DiamondNode,
    DiamondNodeModel,
    RectNode,
    RectNodeModel,
} from "@logicflow/core";
import "@logicflow/core/es/index.css";
import { ElMessage } from "element-plus";
import { onBeforeUnmount, onMounted, ref } from "vue";

import type { ApprovalNodeType, ApprovalWorkflowGraphDto } from "@/shared/types/approval-workflow";

import {
    fromLogicFlowGraph,
    nextNodeCode,
    toLogicFlowGraph,
    type LogicFlowGraphData,
} from "./approval-workflow-graph";

const props = defineProps<{ graph: ApprovalWorkflowGraphDto }>();
const emit = defineEmits<{
    change: [graph: ApprovalWorkflowGraphDto];
    select: [selection: { kind: "node" | "edge"; id: string } | undefined];
}>();

const container = ref<HTMLDivElement>();
let logicFlow: LogicFlow | undefined;
let selected: { kind: "node" | "edge"; id: string } | undefined;

class ApprovalTaskModel extends RectNodeModel {
    setAttributes() {
        this.width = 168;
        this.height = 56;
        this.radius = 10;
        this.text.editable = false;
    }

    getNodeStyle() {
        return { ...super.getNodeStyle(), fill: "#eff6ff", stroke: "#2563eb", strokeWidth: 1.5 };
    }
}

class StartModel extends CircleNodeModel {
    setAttributes() {
        this.r = 28;
        this.text.editable = false;
        this.targetRules.push({ message: "开始节点不能有入线", validate: () => false });
    }

    getNodeStyle() {
        return { ...super.getNodeStyle(), fill: "#ecfdf5", stroke: "#059669", strokeWidth: 2 };
    }
}

class EndModel extends CircleNodeModel {
    setAttributes() {
        this.r = 28;
        this.text.editable = false;
        this.sourceRules.push({ message: "结束节点不能有出线", validate: () => false });
    }

    getNodeStyle() {
        return { ...super.getNodeStyle(), fill: "#f8fafc", stroke: "#475569", strokeWidth: 3 };
    }
}

class GatewayModel extends DiamondNodeModel {
    setAttributes() {
        this.rx = 42;
        this.ry = 34;
        this.text.editable = false;
    }

    getNodeStyle() {
        return { ...super.getNodeStyle(), fill: "#fff7ed", stroke: "#ea580c", strokeWidth: 1.5 };
    }
}

function graphData() {
    return logicFlow?.getGraphData() as LogicFlowGraphData | undefined;
}

function emitChange() {
    const data = graphData();
    if (data) emit("change", fromLogicFlowGraph(data));
}

function addNode(nodeType: Exclude<ApprovalNodeType, "START" | "END">) {
    if (!logicFlow) return;
    const graph = fromLogicFlowGraph(graphData() ?? { nodes: [], edges: [] });
    const isApproval = nodeType === "APPROVAL";
    const code = nextNodeCode(graph, isApproval ? "approval" : "gateway");
    logicFlow.addNode({
        id: code,
        type: isApproval ? "approval-task" : "approval-gateway",
        x: 360 + graph.nodes.length * 24,
        y: 180 + graph.nodes.length * 18,
        text: isApproval ? "审批节点" : "条件分支",
        properties: {
            nodeType,
            nodeName: isApproval ? "审批节点" : "条件分支",
            candidateUserIds: [],
        },
    });
    emitChange();
}

function startDrag(nodeType: Exclude<ApprovalNodeType, "START" | "END">) {
    if (!logicFlow) return;
    const graph = fromLogicFlowGraph(graphData() ?? { nodes: [], edges: [] });
    const isApproval = nodeType === "APPROVAL";
    const code = nextNodeCode(graph, isApproval ? "approval" : "gateway");
    logicFlow.dnd.startDrag({
        id: code,
        type: isApproval ? "approval-task" : "approval-gateway",
        text: isApproval ? "审批节点" : "条件分支",
        properties: { nodeType, candidateUserIds: [] },
    });
}

function deleteSelected() {
    if (!logicFlow || !selected) return;
    if (selected.kind === "node") {
        const node = fromLogicFlowGraph(graphData() ?? { nodes: [], edges: [] }).nodes.find(
            (item) => item.nodeCode === selected?.id,
        );
        if (node?.nodeType === "START" || node?.nodeType === "END") {
            ElMessage.warning("开始和结束节点不可删除");
            return;
        }
        logicFlow.deleteNode(selected.id);
    } else {
        logicFlow.deleteEdge(selected.id);
    }
    selected = undefined;
    emit("select", undefined);
    emitChange();
}

function updateNode(id: string, values: { nodeName?: string; candidateUserIds?: number[] }) {
    if (!logicFlow) return;
    if (values.nodeName !== undefined) logicFlow.updateText(id, values.nodeName);
    logicFlow.setProperties(id, values);
    emitChange();
}

function updateEdge(id: string, values: Record<string, unknown>) {
    logicFlow?.setProperties(id, values);
    emitChange();
}

function focusElement(id: string) {
    logicFlow?.selectElementById(id);
    logicFlow?.focusOn({ id });
}

function undo() {
    logicFlow?.undo();
    emitChange();
}

function redo() {
    logicFlow?.redo();
    emitChange();
}

function zoomIn() {
    logicFlow?.zoom(true);
}

function zoomOut() {
    logicFlow?.zoom(false);
}

function fitView() {
    logicFlow?.fitView(40, 40);
}

defineExpose({
    addNode,
    startDrag,
    deleteSelected,
    updateNode,
    updateEdge,
    focusElement,
    undo,
    redo,
    zoomIn,
    zoomOut,
    fitView,
});

onMounted(() => {
    if (!container.value) return;
    const instance = new LogicFlow({
        container: container.value,
        grid: { size: 20, visible: true, type: "dot", config: { color: "#cbd5e1" } },
        edgeType: "polyline",
        keyboard: {
            enabled: true,
            shortcuts: [{ keys: ["backspace", "delete"], callback: deleteSelected }],
        },
        background: { color: "#f8fafc" },
    });
    instance.register({ type: "approval-start", view: CircleNode, model: StartModel });
    instance.register({ type: "approval-task", view: RectNode, model: ApprovalTaskModel });
    instance.register({ type: "approval-gateway", view: DiamondNode, model: GatewayModel });
    instance.register({ type: "approval-end", view: CircleNode, model: EndModel });
    instance.setTheme({
        polyline: {
            stroke: "#64748b",
            strokeWidth: 1.5,
            hoverStroke: "#2563eb",
            selectedStroke: "#2563eb",
        },
        anchor: { fill: "#fff", stroke: "#2563eb", r: 4 },
        outline: { stroke: "#93c5fd", strokeWidth: 2 },
        edgeText: { fontSize: 12, fill: "#334155", textWidth: 120, background: { fill: "#fff" } },
    });
    instance.on("node:click", ({ data }) => {
        selected = { kind: "node", id: data.id };
        emit("select", selected);
    });
    instance.on("edge:click", ({ data }) => {
        selected = { kind: "edge", id: data.id };
        emit("select", selected);
    });
    instance.on("blank:click", () => {
        selected = undefined;
        emit("select", undefined);
    });
    instance.on("connection:not-allowed", ({ msg }) => ElMessage.warning(msg));
    instance.on("edge:add", ({ data }) => {
        const current = graphData();
        const duplicates = current?.edges.filter(
            (item) =>
                item.sourceNodeId === data.sourceNodeId && item.targetNodeId === data.targetNodeId,
        );
        if (data.sourceNodeId === data.targetNodeId || (duplicates?.length ?? 0) > 1) {
            instance.deleteEdge(data.id);
            ElMessage.warning(
                data.sourceNodeId === data.targetNodeId
                    ? "节点不能连接自身"
                    : "相同节点之间不能重复连线",
            );
        }
        emitChange();
    });
    instance.on("node:drop,node:dnd-add,node:delete,edge:delete", emitChange);
    logicFlow = instance;
    instance.render(toLogicFlowGraph(props.graph) as unknown as LogicFlow.GraphConfigData);
    window.setTimeout(() => instance.fitView(40, 40));
});

onBeforeUnmount(() => logicFlow?.destroy());
</script>

<template>
    <div ref="container" class="approval-canvas" aria-label="审批流程画布" />
</template>

<style scoped>
.approval-canvas {
    width: 100%;
    height: 100%;
    min-height: 560px;
}

:deep(.lf-graph) {
    background-image: radial-gradient(circle, rgb(148 163 184 / 35%) 1px, transparent 1px);
    background-size: 20px 20px;
}
</style>
