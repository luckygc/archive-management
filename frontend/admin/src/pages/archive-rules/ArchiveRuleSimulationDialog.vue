<script setup lang="ts">
import { ref, watch } from "vue";
import { simulateArchiveRuntimeDefinitions } from "@/shared/api/archive-rules";
import { AmDataTable } from "@/shared/components/data-table";
import { requestErrorMessage } from "@/shared/requestError";
import type { ArchiveRuntimeExecutionResult } from "@/shared/types/archive-rules";
import { defaultSimulation, parseObject, triggerPoints, trim } from "./archiveRuleForm";

const simulationOpen = defineModel<boolean>({ required: true });
const simulationSubmitting = ref(false);
const simulationError = ref<string>();
const simulationResult = ref<ArchiveRuntimeExecutionResult>();
const simulation = ref(defaultSimulation());

watch(simulationOpen, (open) => {
    if (open) openSimulation();
});
function openSimulation() {
    simulation.value = defaultSimulation();
    simulationResult.value = undefined;
    simulationError.value = undefined;
}

async function runSimulation() {
    simulationSubmitting.value = true;
    simulationError.value = undefined;
    try {
        const value = simulation.value;
        simulationResult.value = await simulateArchiveRuntimeDefinitions({
            triggerPoint: value.triggerPoint,
            fondsCode: trim(value.fondsCode),
            categoryCode: trim(value.categoryCode),
            archiveLevel: value.triggerPoint.startsWith("VOLUME_") ? "VOLUME" : "ITEM",
            objectTypeCode: "SIMULATION",
            candidateFacts: parseObject(value.candidateFacts, "候选事实 JSON"),
        });
    } catch (error) {
        simulationError.value = requestErrorMessage(error, "试运行失败");
    } finally {
        simulationSubmitting.value = false;
    }
}
</script>

<template>
    <el-dialog
        v-model="simulationOpen"
        title="无副作用试运行"
        width="min(900px, 94vw)"
        destroy-on-close
    >
        <el-alert
            title="试运行复用真实执行核心，但不会写主数据、审计或决策追踪。"
            type="info"
            :closable="false"
            show-icon
        />
        <el-form :model="simulation" label-position="top" class="simulation-form">
            <div class="editor-form__row">
                <el-form-item label="触发点"
                    ><el-select v-model="simulation.triggerPoint"
                        ><el-option
                            v-for="item in triggerPoints"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value" /></el-select></el-form-item
                ><el-form-item label="全宗编码"
                    ><el-input v-model="simulation.fondsCode" /></el-form-item
                ><el-form-item label="分类编码"
                    ><el-input v-model="simulation.categoryCode"
                /></el-form-item>
            </div>
            <el-form-item label="候选事实 JSON"
                ><el-input
                    v-model="simulation.candidateFacts"
                    type="textarea"
                    :rows="8"
                    class="code-input"
            /></el-form-item>
        </el-form>
        <el-alert v-if="simulationError" :title="simulationError" type="error" :closable="false" />
        <template v-if="simulationResult">
            <div class="simulation-summary">
                <el-tag
                    :type="
                        simulationResult.blocking
                            ? 'danger'
                            : simulationResult.warnings.length
                              ? 'warning'
                              : 'success'
                    "
                    >{{
                        simulationResult.blocking
                            ? "将阻断"
                            : simulationResult.warnings.length
                              ? "放行但有警告"
                              : "允许执行"
                    }}</el-tag
                ><span>候选字段变化 {{ Object.keys(simulationResult.assignments).length }} 项</span>
            </div>
            <AmDataTable
                :data="simulationResult.decisions"
                size="small"
                :columns="[
                    { key: 'definitionCode', label: '定义', sortable: true },
                    { key: 'definitionKind', label: '类型', width: 100, sortable: true },
                    { key: 'matched', label: '命中', width: 80, sortable: true },
                    { key: 'result', label: '结果', width: 100 },
                    { key: 'message', label: '消息', sortable: true },
                ]"
            >
                <template #cell-matched="{ row }">{{ row.matched ? "是" : "否" }}</template>
                <template #cell-result="{ row }">
                    <el-tag
                        :type="
                            row.blocking
                                ? 'danger'
                                : row.severity === 'WARNING'
                                  ? 'warning'
                                  : 'info'
                        "
                        >{{ row.blocking ? "阻断" : row.severity }}</el-tag
                    >
                </template>
            </AmDataTable>
            <el-collapse
                ><el-collapse-item title="最终候选事实">
                    <pre>{{ JSON.stringify(simulationResult.candidateFacts, null, 2) }}</pre>
                </el-collapse-item></el-collapse
            >
        </template>
        <template #footer
            ><el-button @click="simulationOpen = false">关闭</el-button
            ><el-button type="primary" :loading="simulationSubmitting" @click="runSimulation"
                >开始试运行</el-button
            ></template
        >
    </el-dialog>
</template>

<style scoped>
.editor-form__row {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
}
.editor-form__row:has(> :nth-child(2):last-child) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
}
.code-input :deep(textarea) {
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 12px;
    line-height: 1.55;
}
.simulation-summary {
    display: flex;
    align-items: center;
    gap: 12px;
}
.simulation-form {
    margin-top: 14px;
}
.simulation-summary {
    margin: 14px 0 10px;
}
pre {
    max-height: 260px;
    overflow: auto;
    margin: 0;
    padding: 12px;
    background: #f5f7f8;
    font-size: 12px;
}
@media (max-width: 640px) {
    .editor-form__row {
        grid-template-columns: 1fr;
    }
}
</style>
