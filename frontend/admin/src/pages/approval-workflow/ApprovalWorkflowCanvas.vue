<script setup lang="ts">
import {
    Delete,
    FullScreen,
    RefreshLeft,
    RefreshRight,
    ZoomIn,
    ZoomOut,
} from "@element-plus/icons-vue";
import { ref } from "vue";
import type {
    ApprovalFlowEdgeDto,
    ApprovalFlowNodeDto,
    ApprovalWorkflowGraphDto,
} from "@/shared/types/approval-workflow";
import ApprovalWorkflowDesigner from "./ApprovalWorkflowDesigner.vue";

defineProps<{ graph: ApprovalWorkflowGraphDto; loading: boolean }>();
const emit = defineEmits<{
    change: [graph: ApprovalWorkflowGraphDto];
    select: [selection?: { kind: "node" | "edge"; id: string }];
}>();
const designer = ref<InstanceType<typeof ApprovalWorkflowDesigner>>();
defineExpose({
    updateNode: (
        id: string,
        values: Partial<Pick<ApprovalFlowNodeDto, "nodeName" | "candidateUserIds">>,
    ) => designer.value?.updateNode(id, values),
    updateEdge: (id: string, values: Partial<ApprovalFlowEdgeDto>) =>
        designer.value?.updateEdge(id, values),
    focusElement: (id: string) => designer.value?.focusElement(id),
});
</script>

<template>
    <div class="designer-canvas">
        <aside class="designer-palette" aria-label="节点工具箱">
            <div class="panel-title">节点</div>
            <button
                class="palette-item"
                title="拖拽到画布；键盘按回车可直接添加"
                @mousedown="designer?.startDrag('APPROVAL')"
                @keydown.enter.prevent="designer?.addNode('APPROVAL')"
                @keydown.space.prevent="designer?.addNode('APPROVAL')"
            >
                <span class="palette-shape palette-shape--task" />
                <span><strong>审批节点</strong><small>指定用户办理</small></span>
            </button>
            <button
                class="palette-item"
                title="拖拽到画布；键盘按回车可直接添加"
                @mousedown="designer?.startDrag('EXCLUSIVE_GATEWAY')"
                @keydown.enter.prevent="designer?.addNode('EXCLUSIVE_GATEWAY')"
                @keydown.space.prevent="designer?.addNode('EXCLUSIVE_GATEWAY')"
            >
                <span class="palette-shape palette-shape--gateway" />
                <span><strong>条件分支</strong><small>按业务字段路由</small></span>
            </button>
            <div class="palette-help">
                拖入节点后，从节点锚点拉出连线。开始与结束节点已自动创建。
            </div>
        </aside>

        <main class="designer-stage">
            <div class="canvas-toolbar" aria-label="画布工具栏">
                <el-button-group>
                    <el-button
                        :icon="RefreshLeft"
                        title="撤销（Ctrl+Z）"
                        @click="designer?.undo()"
                    />
                    <el-button
                        :icon="RefreshRight"
                        title="重做（Ctrl+Y）"
                        @click="designer?.redo()"
                    />
                </el-button-group>
                <el-button-group>
                    <el-button :icon="ZoomOut" title="缩小" @click="designer?.zoomOut()" />
                    <el-button :icon="ZoomIn" title="放大" @click="designer?.zoomIn()" />
                    <el-button :icon="FullScreen" title="适应画布" @click="designer?.fitView()" />
                </el-button-group>
                <el-button
                    :icon="Delete"
                    title="删除选中元素"
                    @click="designer?.deleteSelected()"
                />
            </div>
            <ApprovalWorkflowDesigner
                v-if="!loading"
                ref="designer"
                :graph="graph"
                @change="emit('change', $event)"
                @select="emit('select', $event)"
            />
        </main>
    </div>
</template>

<style scoped>
.designer-canvas {
    display: grid;
    min-height: 0;
    grid-template-columns: 220px minmax(420px, 1fr);
}
.designer-palette {
    overflow: auto;
    padding: 18px 16px;
    background: #fff;
}
.designer-palette {
    border-right: 1px solid #e2e8f0;
}
.panel-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
    color: #334155;
    font-size: 13px;
    font-weight: 650;
}
.palette-item {
    width: 100%;
    border: 1px solid #e2e8f0;
    background: #fff;
    color: #0f172a;
    cursor: pointer;
    text-align: left;
}
.palette-item {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
    padding: 12px;
    border-radius: 8px;
}
.palette-item:hover {
    border-color: #93c5fd;
    background: #f8fbff;
}
.palette-item strong {
    display: block;
    font-size: 13px;
}
.palette-item small {
    display: block;
    margin-top: 3px;
    color: #64748b;
}
.palette-shape {
    display: block;
    flex: 0 0 auto;
}
.palette-shape--task {
    width: 34px;
    height: 24px;
    border: 2px solid #2563eb;
    border-radius: 6px;
    background: #eff6ff;
}
.palette-shape--gateway {
    width: 25px;
    height: 25px;
    margin: 4px;
    transform: rotate(45deg);
    border: 2px solid #ea580c;
    background: #fff7ed;
}
.palette-help {
    margin-top: 18px;
    padding: 12px;
    border: 1px solid #dbeafe;
    background: #f8fafc;
    color: #64748b;
    font-size: 12px;
    line-height: 1.65;
}
.designer-stage {
    position: relative;
    min-width: 0;
    min-height: 0;
    overflow: hidden;
}
.canvas-toolbar {
    position: absolute;
    z-index: 3;
    top: 14px;
    left: 50%;
    display: flex;
    gap: 8px;
    transform: translateX(-50%);
    padding: 6px;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    background: rgb(255 255 255 / 94%);
    box-shadow: 0 4px 14px rgb(15 23 42 / 8%);
}
@media (max-width: 1180px) {
    .designer-canvas {
        grid-template-columns: 176px minmax(360px, 1fr);
    }
}
@media (max-width: 900px) {
    .designer-canvas {
        display: flex;
        flex-direction: column;
    }
    .designer-palette {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 8px;
        border-right: 0;
        border-bottom: 1px solid #e2e8f0;
    }
    .designer-palette .panel-title,
    .palette-help {
        grid-column: 1 / -1;
    }
    .designer-stage {
        height: 560px;
    }
}
</style>
