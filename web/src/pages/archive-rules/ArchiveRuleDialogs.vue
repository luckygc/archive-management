<script setup lang="ts">
import { ElMessage, type FormInstance } from "element-plus";
import { ref } from "vue";

import { createArchiveRule, executeArchiveRules } from "@/shared/api/archive-rules";
import type { ArchiveLevel } from "@/shared/types/archive-metadata";
import type {
    ArchiveRuleDecisionDto,
    ArchiveRuleEffectType,
    ArchiveRuleType,
} from "@/shared/types/archive-rules";

const props = defineProps<{ schemeVersionId?: number }>();
const emit = defineEmits<{ changed: [] }>();
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
        schemeVersionId: props.schemeVersionId,
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
function openRule() {
    ruleForm.value = { ...defaultRuleForm(), schemeVersionId: props.schemeVersionId };
    ruleModalOpen.value = true;
}
function openExecute() {
    executeForm.value = defaultExecuteForm();
    decisions.value = [];
    executeModalOpen.value = true;
}
async function submitRule() {
    if (!(await ruleFormRef.value?.validate().catch(() => false))) return;
    ruleSubmitting.value = true;
    try {
        const values = ruleForm.value;
        await createArchiveRule({
            ...values,
            schemeVersionId: values.schemeVersionId!,
            scopeFondsCode: values.scopeFondsCode || undefined,
            scopeCategoryCode: values.scopeCategoryCode || undefined,
            conditionJson: parseJson(values.conditionJson),
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
        emit("changed");
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        ruleSubmitting.value = false;
    }
}
async function submitExecute() {
    if (!(await executeFormRef.value?.validate().catch(() => false))) return;
    executeSubmitting.value = true;
    try {
        const values = executeForm.value;
        decisions.value = (
            await executeArchiveRules({
                ...values,
                schemeVersionId: values.schemeVersionId!,
                fondsCode: values.fondsCode || undefined,
                categoryCode: values.categoryCode || undefined,
                objectTypeCode: values.objectTypeCode || undefined,
                facts: parseJson(values.facts),
            })
        ).items;
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
defineExpose({ openExecute, openRule });
</script>

<template>
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
        <el-table :data="decisions" size="small"
            ><el-table-column prop="ruleCode" label="规则" /><el-table-column
                label="命中"
                width="80"
                ><template #default="{ row }">{{
                    row.matched ? "是" : "否"
                }}</template></el-table-column
            ><el-table-column prop="severity" label="级别" width="90" /><el-table-column
                label="阻断"
                width="80"
                ><template #default="{ row }">{{
                    row.blocking ? "是" : "否"
                }}</template></el-table-column
            ><el-table-column prop="message" label="消息"
        /></el-table>
        <template #footer
            ><el-button @click="executeModalOpen = false">取消</el-button
            ><el-button type="primary" :loading="executeSubmitting" @click="submitExecute"
                >确定</el-button
            ></template
        >
    </el-dialog>
</template>
