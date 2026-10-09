<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, ref, watch } from "vue";
import {
    deleteArchiveRuntimeDefinition,
    disableArchiveRuntimeDefinition,
    enableArchiveRuntimeDefinition,
    listArchiveRuntimeDefinitions,
    publishArchiveRuntimeDefinition,
} from "@/shared/api/archive-rules";
import { requestErrorMessage } from "@/shared/requestError";
import type {
    ArchiveRuntimeDefinitionDto,
    ArchiveRuntimeStatus,
    ArchiveRuntimeTriggerPoint,
} from "@/shared/types/archive-rules";
import ArchiveRulesList from "./ArchiveRulesList.vue";
import ArchiveRuleEditorDialog from "./ArchiveRuleEditorDialog.vue";
import ArchiveRuleSimulationDialog from "./ArchiveRuleSimulationDialog.vue";

const status = ref<ArchiveRuntimeStatus>();
const triggerPoint = ref<ArchiveRuntimeTriggerPoint>();
const definitions = ref<ArchiveRuntimeDefinitionDto[]>([]);
const loading = ref(false);
const loadError = ref<string>();

const editorOpen = ref(false);
const editingDefinition = ref<ArchiveRuntimeDefinitionDto>();
const simulationOpen = ref(false);
watch(status, () => void loadDefinitions());
onMounted(() => void loadDefinitions());
async function loadDefinitions() {
    loading.value = true;
    try {
        const response = await listArchiveRuntimeDefinitions(status.value);
        definitions.value = triggerPoint.value
            ? response.items.filter((item) => item.triggerPoint === triggerPoint.value)
            : response.items;
        loadError.value = undefined;
    } catch (error) {
        loadError.value = requestErrorMessage(error, "运行时定义加载失败");
    } finally {
        loading.value = false;
    }
}

function openCreate() {
    editingDefinition.value = undefined;
    editorOpen.value = true;
}
function openEdit(row: unknown) {
    editingDefinition.value = row as ArchiveRuntimeDefinitionDto;
    editorOpen.value = true;
}
function openSimulation() {
    simulationOpen.value = true;
}
async function publishDefinition(value: unknown) {
    const row = value as ArchiveRuntimeDefinitionDto;
    try {
        await publishArchiveRuntimeDefinition(row.id);
        ElMessage.success("定义已发布并锁定语义");
        await loadDefinitions();
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "定义发布失败"));
    }
}

async function changeEnabled(value: unknown, enabled: boolean) {
    const row = value as ArchiveRuntimeDefinitionDto;
    try {
        await (enabled
            ? enableArchiveRuntimeDefinition(row.id)
            : disableArchiveRuntimeDefinition(row.id));
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "启停失败"));
    } finally {
        await loadDefinitions();
    }
}

async function removeDefinition(value: unknown) {
    const row = value as ArchiveRuntimeDefinitionDto;
    try {
        await ElMessageBox.confirm(`删除草稿“${row.definitionName}”？`, "删除运行时定义", {
            type: "warning",
        });
        await deleteArchiveRuntimeDefinition(row.id);
        await loadDefinitions();
    } catch (error) {
        if (error !== "cancel" && error !== "close") {
            ElMessage.error(requestErrorMessage(error, "定义删除失败"));
        }
    }
}
</script>

<template>
    <section class="am-page runtime-page">
        <div class="am-page__header runtime-header">
            <div>
                <p class="runtime-eyebrow">RUNTIME POLICY</p>
                <h1>运行时约束与规则</h1>
                <p class="runtime-subtitle">用户定义条件，系统只执行固定触发点与固定动作。</p>
            </div>
            <div class="am-page__actions">
                <el-button @click="openSimulation">试运行</el-button>
                <el-button type="primary" @click="openCreate">新建定义</el-button>
            </div>
        </div>
        <ArchiveRulesList
            v-model:status="status"
            v-model:trigger-point="triggerPoint"
            :definitions="definitions"
            :loading="loading"
            :load-error="loadError"
            @refresh="loadDefinitions"
            @create="openCreate"
            @edit="openEdit"
            @publish="publishDefinition"
            @enable="changeEnabled"
            @remove="removeDefinition"
        />
        <ArchiveRuleEditorDialog
            v-model="editorOpen"
            :definition="editingDefinition"
            @saved="loadDefinitions"
        />
        <ArchiveRuleSimulationDialog v-model="simulationOpen" />
    </section>
</template>

<style scoped>
.runtime-page {
    --runtime-ink: #18222d;
    --runtime-muted: #667281;
    --runtime-line: #dfe5e8;
}
.runtime-header {
    align-items: flex-end;
}
.runtime-eyebrow {
    margin: 0 0 6px;
    color: var(--el-color-primary);
    font:
        700 11px/1.2 ui-monospace,
        monospace;
    letter-spacing: 0.16em;
}
.runtime-subtitle {
    margin: 6px 0 0;
    color: var(--runtime-muted);
    font-size: 13px;
}
@media (max-width: 640px) {
    .runtime-header {
        align-items: flex-start;
    }
}
</style>
