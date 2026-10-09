<script setup lang="ts">
import { Connection } from "@element-plus/icons-vue";
import { computed } from "vue";
import type {
    ApprovalConditionOperator,
    ApprovalFlowEdgeDto,
    ApprovalFlowNodeDto,
    ApprovalWorkflowGraphDto,
} from "@/shared/types/approval-workflow";
import type { AuthenticationUserOptionDto } from "@/shared/types/authentication";
import type { GraphIssue } from "./approval-workflow-graph";

const props = defineProps<{
    graph: ApprovalWorkflowGraphDto;
    selected?: { kind: "node" | "edge"; id: string };
    definitionId?: number;
    userOptions: AuthenticationUserOptionDto[];
    issues: GraphIssue[];
}>();
const form = defineModel<{ definitionCode: string; definitionName: string; businessType: string }>({
    required: true,
});
const emit = defineEmits<{
    updateNode: [
        id: string,
        values: Partial<Pick<ApprovalFlowNodeDto, "nodeName" | "candidateUserIds">>,
    ];
    updateEdge: [id: string, values: Partial<ApprovalFlowEdgeDto>];
    defaultFlow: [id: string, value: boolean];
    metadataChange: [];
    focusIssue: [elementId?: string];
}>();
const selectedNode = computed(() =>
    props.selected?.kind === "node"
        ? props.graph.nodes.find((item) => item.nodeCode === props.selected?.id)
        : undefined,
);
const selectedEdge = computed(() =>
    props.selected?.kind === "edge"
        ? props.graph.edges.find((item) => item.edgeCode === props.selected?.id)
        : undefined,
);
const selectedEdgeSource = computed(() =>
    props.graph.nodes.find((item) => item.nodeCode === selectedEdge.value?.sourceNodeCode),
);

function updateNode(values: Partial<Pick<ApprovalFlowNodeDto, "nodeName" | "candidateUserIds">>) {
    if (selectedNode.value) emit("updateNode", selectedNode.value.nodeCode, values);
}
function updateEdge(values: Partial<ApprovalFlowEdgeDto>) {
    if (selectedEdge.value) emit("updateEdge", selectedEdge.value.edgeCode, values);
}
function updateCondition(
    key: "field" | "operator" | "values",
    value: string | ApprovalConditionOperator | string[],
) {
    if (!selectedEdge.value) return;
    const current = selectedEdge.value.condition ?? { field: "", operator: "EQUALS", values: [] };
    updateEdge({ condition: { ...current, [key]: value } });
}

function userLabel(userId: number) {
    const user = props.userOptions.find((item) => item.id === userId);
    return user ? `${user.displayName}（${user.username}）` : `用户 #${userId}`;
}
</script>

<template>
    <aside class="designer-properties" aria-label="属性面板">
        <template v-if="selectedNode">
            <div class="panel-title">节点属性</div>
            <el-form label-position="top">
                <el-form-item label="节点编码"
                    ><el-input :model-value="selectedNode.nodeCode" disabled
                /></el-form-item>
                <el-form-item label="节点名称">
                    <el-input
                        :model-value="selectedNode.nodeName"
                        maxlength="100"
                        @update:model-value="updateNode({ nodeName: String($event) })"
                    />
                </el-form-item>
                <el-form-item v-if="selectedNode.nodeType === 'APPROVAL'" label="候选用户" required>
                    <el-select
                        :model-value="selectedNode.candidateUserIds"
                        multiple
                        filterable
                        collapse-tags
                        :max-collapse-tags="2"
                        placeholder="请选择办理人"
                        @update:model-value="updateNode({ candidateUserIds: $event as number[] })"
                    >
                        <el-option
                            v-for="user in userOptions"
                            :key="user.id"
                            :label="userLabel(user.id)"
                            :value="user.id"
                        />
                    </el-select>
                </el-form-item>
            </el-form>
        </template>
        <template v-else-if="selectedEdge">
            <div class="panel-title">连线属性</div>
            <el-form label-position="top">
                <el-form-item label="连线编码"
                    ><el-input :model-value="selectedEdge.edgeCode" disabled
                /></el-form-item>
                <template v-if="selectedEdgeSource?.nodeType === 'EXCLUSIVE_GATEWAY'">
                    <el-form-item label="默认分支">
                        <el-switch
                            :model-value="selectedEdge.defaultFlow"
                            @update:model-value="
                                selectedEdge &&
                                emit('defaultFlow', selectedEdge.edgeCode, Boolean($event))
                            "
                        />
                    </el-form-item>
                    <template v-if="!selectedEdge.defaultFlow">
                        <el-form-item label="业务字段" required>
                            <el-input
                                :model-value="selectedEdge.condition?.field"
                                placeholder="例如 archive_type"
                                @update:model-value="updateCondition('field', String($event))"
                            />
                        </el-form-item>
                        <el-form-item label="运算符" required>
                            <el-select
                                :model-value="selectedEdge.condition?.operator ?? 'EQUALS'"
                                @update:model-value="
                                    updateCondition('operator', $event as ApprovalConditionOperator)
                                "
                            >
                                <el-option label="等于" value="EQUALS" />
                                <el-option label="不等于" value="NOT_EQUALS" />
                                <el-option label="属于任一值" value="IN" />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="比较值" required>
                            <el-select
                                :model-value="selectedEdge.condition?.values ?? []"
                                multiple
                                filterable
                                allow-create
                                default-first-option
                                placeholder="输入后回车，可添加多个"
                                @update:model-value="updateCondition('values', $event as string[])"
                            />
                        </el-form-item>
                    </template>
                </template>
                <el-alert v-else type="info" :closable="false" title="普通连线无需配置条件" />
            </el-form>
        </template>
        <template v-else>
            <div class="panel-title">流程信息</div>
            <el-form label-position="top">
                <el-form-item label="定义编码" required>
                    <el-input
                        v-model="form.definitionCode"
                        :disabled="Boolean(definitionId)"
                        placeholder="例如 archive_intake_flow"
                        @input="emit('metadataChange')"
                    />
                </el-form-item>
                <el-form-item label="定义名称" required
                    ><el-input
                        v-model="form.definitionName"
                        maxlength="100"
                        @input="emit('metadataChange')"
                /></el-form-item>
                <el-form-item label="业务类型" required
                    ><el-input
                        v-model="form.businessType"
                        placeholder="例如 archive_intake"
                        @input="emit('metadataChange')"
                /></el-form-item>
            </el-form>
        </template>

        <div class="issue-panel">
            <div class="panel-title">
                发布检查
                <el-tag :type="issues.length ? 'warning' : 'success'" size="small">{{
                    issues.length
                }}</el-tag>
            </div>
            <el-empty v-if="issues.length === 0" :image-size="42" description="流程结构完整" />
            <button
                v-for="(issue, index) in issues"
                v-else
                :key="`${issue.elementId}-${index}`"
                class="issue-item"
                @click="emit('focusIssue', issue.elementId)"
            >
                <Connection /> <span>{{ issue.message }}</span>
            </button>
        </div>
    </aside>
</template>

<style scoped>
.designer-properties {
    overflow: auto;
    padding: 18px 16px;
    background: #fff;
}
.designer-properties {
    border-left: 1px solid #e2e8f0;
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
.issue-item {
    width: 100%;
    border: 1px solid #e2e8f0;
    background: #fff;
    color: #0f172a;
    cursor: pointer;
    text-align: left;
}
.issue-panel {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid #e2e8f0;
}
.issue-item {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    margin-bottom: 8px;
    padding: 9px 10px;
    border-color: #fed7aa;
    border-radius: 6px;
    background: #fff7ed;
    color: #9a3412;
    font-size: 12px;
    line-height: 1.45;
}
.issue-item svg {
    width: 15px;
    flex: 0 0 auto;
    margin-top: 1px;
}
:deep(.el-select) {
    width: 100%;
}
@media (max-width: 900px) {
    .designer-properties {
        border-top: 1px solid #e2e8f0;
        border-left: 0;
    }
}
</style>
