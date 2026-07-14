<script setup lang="ts">
import { ElMessage, type FormInstance } from "element-plus";
import { onMounted, ref, watch } from "vue";

import {
    createArchiveRule,
    disableArchiveRule,
    enableArchiveRule,
    executeArchiveRules,
    listArchiveRules,
    publishArchiveRule,
} from "@/shared/api/archive-rules";
import type { ArchiveLevel } from "@/shared/types/archive-metadata";
import type {
    ArchiveRuleDecisionDto,
    ArchiveRuleDto,
    ArchiveRuleEffectType,
    ArchiveRuleStatus,
    ArchiveRuleType,
} from "@/shared/types/archive-rules";

const ruleTypes: ArchiveRuleType[] = [
    "VALIDATION",
    "DERIVATION",
    "REFERENCE_CODE",
    "RETENTION",
    "ACCESS",
    "QUALITY",
    "TRANSFER",
    "FILING",
    "EXPORT",
];
const effectTypes: ArchiveRuleEffectType[] = [
    "VALIDATION_ERROR",
    "WARNING",
    "SUGGEST_VALUE",
    "DERIVED_VALUE",
    "REQUIRE_REVIEW",
    "REQUIRE_QUALITY_CHECK",
    "DENY_ACCESS",
    "MASK_FIELD",
    "INCLUDE_IN_PACKAGE",
];

const schemeVersionId = ref<number>();
const status = ref<ArchiveRuleStatus>();
const rules = ref<ArchiveRuleDto[]>([]);
const loading = ref(false);
const ruleModalOpen = ref(false);
const executeModalOpen = ref(false);
const ruleSubmitting = ref(false);
const executeSubmitting = ref(false);
const ruleFormRef = ref<FormInstance>();
const executeFormRef = ref<FormInstance>();
const decisions = ref<ArchiveRuleDecisionDto[]>([]);
const ruleForm = ref(defaultRuleForm());
const executeForm = ref(defaultExecuteForm());

function defaultRuleForm() {
    return {
        schemeVersionId: undefined as number | undefined,
        ruleCode: "",
        ruleName: "",
        ruleType: "VALIDATION" as ArchiveRuleType,
        triggerCode: "",
        scopeFondsCode: "",
        scopeCategoryCode: "",
        scopeArchiveLevel: undefined as ArchiveLevel | undefined,
        priority: 0,
        conditionJson: '{"field":"fixed.archiveYear","operator":"GTE","value":2020}',
        effectType: "VALIDATION_ERROR" as ArchiveRuleEffectType,
        effectParams: '{"message":"规则命中"}',
        enabled: true,
    };
}

function defaultExecuteForm() {
    return {
        schemeVersionId: schemeVersionId.value,
        triggerCode: "BEFORE_SAVE",
        fondsCode: "",
        categoryCode: "",
        objectTypeCode: "ARCHIVE_ITEM",
        archiveLevel: "ITEM" as ArchiveLevel,
        facts: '{"fixed.archiveYear":2026}',
        includeSkipped: false,
        recordTrace: false,
    };
}

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

watch([schemeVersionId, status], () => void loadRules());
onMounted(() => void loadRules());

function openRuleModal() {
    ruleForm.value = { ...defaultRuleForm(), schemeVersionId: schemeVersionId.value };
    ruleModalOpen.value = true;
}

function openExecuteModal() {
    executeForm.value = defaultExecuteForm();
    executeModalOpen.value = true;
}

async function submitRule() {
    if (!(await ruleFormRef.value?.validate().catch(() => false))) return;
    ruleSubmitting.value = true;
    try {
        const values = ruleForm.value;
        await createArchiveRule({
            schemeVersionId: values.schemeVersionId!,
            ruleCode: values.ruleCode,
            ruleName: values.ruleName,
            ruleType: values.ruleType,
            triggerCode: values.triggerCode,
            scopeFondsCode: values.scopeFondsCode || undefined,
            scopeCategoryCode: values.scopeCategoryCode || undefined,
            scopeArchiveLevel: values.scopeArchiveLevel,
            priority: values.priority,
            conditionJson: parseJson(values.conditionJson),
            enabled: values.enabled,
            effects: [
                {
                    effectType: values.effectType,
                    effectOrder: 0,
                    effectParams: parseJson(values.effectParams || "{}"),
                },
            ],
        });
        ElMessage.success("规则已创建");
        ruleModalOpen.value = false;
        await loadRules();
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        ruleSubmitting.value = false;
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
        await loadRules();
    } catch (error) {
        ElMessage.error((error as Error).message);
        await loadRules();
    }
}

async function submitExecute() {
    if (!(await executeFormRef.value?.validate().catch(() => false))) return;
    executeSubmitting.value = true;
    try {
        const values = executeForm.value;
        const response = await executeArchiveRules({
            schemeVersionId: values.schemeVersionId!,
            triggerCode: values.triggerCode,
            fondsCode: values.fondsCode || undefined,
            categoryCode: values.categoryCode || undefined,
            objectTypeCode: values.objectTypeCode || undefined,
            archiveLevel: values.archiveLevel,
            facts: parseJson(values.facts),
            includeSkipped: values.includeSkipped,
            recordTrace: values.recordTrace,
        });
        decisions.value = response.items;
        ElMessage.success("规则试算完成");
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        executeSubmitting.value = false;
    }
}

function parseJson(value: string) {
    try {
        return JSON.parse(value || "{}") as Record<string, unknown>;
    } catch {
        throw new Error("JSON 格式不正确");
    }
}
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
                    <el-option label="草稿" value="DRAFT" /><el-option
                        label="已发布"
                        value="PUBLISHED"
                    />
                </el-select>
                <el-button type="primary" @click="openRuleModal">新建规则</el-button>
                <el-button @click="openExecuteModal">规则试算</el-button>
            </div>
        </div>
        <el-card shadow="never">
            <el-table v-loading="loading" :data="rules" row-key="id">
                <el-table-column prop="ruleCode" label="编码" width="180" />
                <el-table-column prop="ruleName" label="名称" />
                <el-table-column prop="ruleType" label="类型" width="130" />
                <el-table-column prop="triggerCode" label="触发点" width="130" />
                <el-table-column prop="priority" label="优先级" width="90" />
                <el-table-column label="状态" width="100"
                    ><template #default="{ row }"
                        ><el-tag :type="row.status === 'PUBLISHED' ? 'primary' : 'info'">{{
                            row.status === "PUBLISHED" ? "已发布" : "草稿"
                        }}</el-tag></template
                    ></el-table-column
                >
                <el-table-column label="启用" width="90"
                    ><template #default="{ row }"
                        ><el-switch
                            :model-value="row.enabled"
                            @change="changeEnabled(row, Boolean($event))" /></template
                ></el-table-column>
                <el-table-column label="操作" width="110"
                    ><template #default="{ row }"
                        ><el-button
                            size="small"
                            :disabled="row.status !== 'DRAFT'"
                            @click="publishRule(row.id)"
                            >发布</el-button
                        ></template
                    ></el-table-column
                >
            </el-table>
        </el-card>

        <el-dialog v-model="ruleModalOpen" title="新建规则" width="720px" destroy-on-close>
            <el-form ref="ruleFormRef" :model="ruleForm" label-position="top">
                <el-form-item
                    label="治理版本 ID"
                    prop="schemeVersionId"
                    :rules="[{ required: true, message: '请输入治理版本 ID' }]"
                    ><el-input-number
                        v-model="ruleForm.schemeVersionId"
                        :min="1"
                        controls-position="right"
                /></el-form-item>
                <el-form-item
                    label="规则编码"
                    prop="ruleCode"
                    :rules="[{ required: true, message: '请输入规则编码' }]"
                    ><el-input v-model="ruleForm.ruleCode"
                /></el-form-item>
                <el-form-item
                    label="规则名称"
                    prop="ruleName"
                    :rules="[{ required: true, message: '请输入规则名称' }]"
                    ><el-input v-model="ruleForm.ruleName"
                /></el-form-item>
                <el-form-item label="规则类型"
                    ><el-select v-model="ruleForm.ruleType"
                        ><el-option
                            v-for="value in ruleTypes"
                            :key="value"
                            :label="value"
                            :value="value" /></el-select
                ></el-form-item>
                <el-form-item
                    label="触发点"
                    prop="triggerCode"
                    :rules="[{ required: true, message: '请输入触发点' }]"
                    ><el-input v-model="ruleForm.triggerCode" placeholder="BEFORE_SAVE"
                /></el-form-item>
                <el-form-item label="优先级"
                    ><el-input-number v-model="ruleForm.priority" controls-position="right"
                /></el-form-item>
                <el-form-item
                    label="条件树 JSON"
                    prop="conditionJson"
                    :rules="[{ required: true, message: '请输入条件树 JSON' }]"
                    ><el-input v-model="ruleForm.conditionJson" type="textarea" :rows="5"
                /></el-form-item>
                <el-form-item label="effect 类型"
                    ><el-select v-model="ruleForm.effectType"
                        ><el-option
                            v-for="value in effectTypes"
                            :key="value"
                            :label="value"
                            :value="value" /></el-select
                ></el-form-item>
                <el-form-item label="effect 参数 JSON"
                    ><el-input v-model="ruleForm.effectParams" type="textarea" :rows="3"
                /></el-form-item>
                <el-form-item label="启用"><el-switch v-model="ruleForm.enabled" /></el-form-item>
            </el-form>
            <template #footer
                ><el-button @click="ruleModalOpen = false">取消</el-button
                ><el-button type="primary" :loading="ruleSubmitting" @click="submitRule"
                    >确定</el-button
                ></template
            >
        </el-dialog>

        <el-dialog v-model="executeModalOpen" title="规则试算" width="760px" destroy-on-close>
            <el-form ref="executeFormRef" :model="executeForm" label-position="top">
                <el-form-item
                    label="治理版本 ID"
                    prop="schemeVersionId"
                    :rules="[{ required: true, message: '请输入治理版本 ID' }]"
                    ><el-input-number
                        v-model="executeForm.schemeVersionId"
                        :min="1"
                        controls-position="right"
                /></el-form-item>
                <el-form-item
                    label="触发点"
                    prop="triggerCode"
                    :rules="[{ required: true, message: '请输入触发点' }]"
                    ><el-input v-model="executeForm.triggerCode"
                /></el-form-item>
                <el-form-item label="对象类型"
                    ><el-input v-model="executeForm.objectTypeCode"
                /></el-form-item>
                <el-form-item label="对象层级"
                    ><el-select v-model="executeForm.archiveLevel"
                        ><el-option label="条目" value="ITEM" /><el-option
                            label="案卷"
                            value="VOLUME" /></el-select
                ></el-form-item>
                <el-form-item
                    label="事实 JSON"
                    prop="facts"
                    :rules="[{ required: true, message: '请输入事实 JSON' }]"
                    ><el-input v-model="executeForm.facts" type="textarea" :rows="5"
                /></el-form-item>
                <el-form-item label="返回未命中"
                    ><el-switch v-model="executeForm.includeSkipped"
                /></el-form-item>
                <el-form-item label="保存追踪"
                    ><el-switch v-model="executeForm.recordTrace"
                /></el-form-item>
            </el-form>
            <el-table :data="decisions" size="small">
                <el-table-column prop="ruleCode" label="规则" /><el-table-column
                    label="命中"
                    width="80"
                    ><template #default="{ row }">{{
                        row.matched ? "是" : "否"
                    }}</template></el-table-column
                >
                <el-table-column prop="severity" label="级别" width="90" /><el-table-column
                    label="阻断"
                    width="80"
                    ><template #default="{ row }">{{
                        row.blocking ? "是" : "否"
                    }}</template></el-table-column
                ><el-table-column prop="message" label="消息" />
            </el-table>
            <template #footer
                ><el-button @click="executeModalOpen = false">取消</el-button
                ><el-button type="primary" :loading="executeSubmitting" @click="submitExecute"
                    >确定</el-button
                ></template
            >
        </el-dialog>
    </section>
</template>
