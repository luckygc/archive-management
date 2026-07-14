<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, ref, watch } from "vue";

import {
    disableArchiveRule,
    enableArchiveRule,
    listArchiveRules,
    publishArchiveRule,
} from "@/shared/api/archive-rules";
import type { ArchiveRuleDto, ArchiveRuleStatus } from "@/shared/types/archive-rules";
import ArchiveRuleDialogs from "./ArchiveRuleDialogs.vue";

const schemeVersionId = ref<number>();
const status = ref<ArchiveRuleStatus>();
const rules = ref<ArchiveRuleDto[]>([]);
const loading = ref(false);
const dialogs = ref<InstanceType<typeof ArchiveRuleDialogs>>();

async function loadRules() {
    if (schemeVersionId.value == null) {
        rules.value = [];
        return;
    }
    loading.value = true;
    try {
        rules.value = (await listArchiveRules(schemeVersionId.value, status.value)).items;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        loading.value = false;
    }
}
async function publishRule(id: number) {
    try {
        await publishArchiveRule(id);
        ElMessage.success("规则已发布");
        await loadRules();
    } catch (error) {
        ElMessage.error((error as Error).message);
    }
}
async function changeEnabled(value: unknown, enabled: boolean) {
    const row = value as ArchiveRuleDto;
    try {
        await (enabled ? enableArchiveRule(row.id) : disableArchiveRule(row.id));
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        await loadRules();
    }
}
watch([schemeVersionId, status], () => void loadRules());
onMounted(() => void loadRules());
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>本地规则</h1>
            <div class="am-page__actions">
                <el-input-number
                    v-model="schemeVersionId"
                    :min="1"
                    placeholder="治理版本 ID"
                    controls-position="right"
                />
                <el-select v-model="status" clearable placeholder="状态" style="width: 120px">
                    <el-option label="草稿" value="DRAFT" />
                    <el-option label="已发布" value="PUBLISHED" />
                </el-select>
                <el-button type="primary" @click="dialogs?.openRule()">新建规则</el-button>
                <el-button @click="dialogs?.openExecute()">规则试算</el-button>
            </div>
        </div>
        <el-card shadow="never">
            <el-table v-loading="loading" :data="rules" row-key="id">
                <el-table-column prop="ruleCode" label="编码" width="180" />
                <el-table-column prop="ruleName" label="名称" />
                <el-table-column prop="ruleType" label="类型" width="130" />
                <el-table-column prop="triggerCode" label="触发点" width="130" />
                <el-table-column prop="priority" label="优先级" width="90" />
                <el-table-column label="状态" width="100">
                    <template #default="{ row }"
                        ><el-tag :type="row.status === 'PUBLISHED' ? 'primary' : 'info'">{{
                            row.status === "PUBLISHED" ? "已发布" : "草稿"
                        }}</el-tag></template
                    >
                </el-table-column>
                <el-table-column label="启用" width="90">
                    <template #default="{ row }"
                        ><el-switch
                            :model-value="row.enabled"
                            @change="changeEnabled(row, Boolean($event))"
                    /></template>
                </el-table-column>
                <el-table-column label="操作" width="110">
                    <template #default="{ row }"
                        ><el-button
                            size="small"
                            :disabled="row.status !== 'DRAFT'"
                            @click="publishRule(row.id)"
                            >发布</el-button
                        ></template
                    >
                </el-table-column>
            </el-table>
        </el-card>
        <ArchiveRuleDialogs
            ref="dialogs"
            :scheme-version-id="schemeVersionId"
            @changed="loadRules"
        />
    </section>
</template>
