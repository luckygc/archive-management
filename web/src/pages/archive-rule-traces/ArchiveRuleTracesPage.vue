<script setup lang="ts">
import { ElMessage, type FormInstance } from "element-plus";
import { ref } from "vue";

import { searchArchiveRuleTraces } from "@/shared/api/archive";
import type { ArchiveRuleTraceDto, ArchiveRuleType } from "@/shared/types/archive";

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

const formRef = ref<FormInstance>();
const form = ref({
    schemeVersionId: undefined as number | undefined,
    triggerCode: "",
    objectTypeCode: "",
    objectId: undefined as number | undefined,
    ruleType: undefined as ArchiveRuleType | undefined,
    limit: 100,
});
const items = ref<ArchiveRuleTraceDto[]>([]);
const loading = ref(false);

async function submitSearch() {
    const valid = await formRef.value?.validate().catch(() => false);
    if (valid === false) return;
    loading.value = true;
    try {
        const response = await searchArchiveRuleTraces({
            schemeVersionId: form.value.schemeVersionId,
            triggerCode: form.value.triggerCode || undefined,
            objectTypeCode: form.value.objectTypeCode || undefined,
            objectId: form.value.objectId,
            ruleType: form.value.ruleType,
            limit: form.value.limit,
        });
        items.value = response.items;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        loading.value = false;
    }
}

function resetForm() {
    formRef.value?.resetFields();
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header"><h1>规则追踪</h1></div>
        <el-card class="am-page__filter" shadow="never">
            <el-form ref="formRef" :model="form" inline>
                <el-form-item label="治理版本" prop="schemeVersionId">
                    <el-input-number
                        v-model="form.schemeVersionId"
                        :min="1"
                        controls-position="right"
                    />
                </el-form-item>
                <el-form-item label="触发点" prop="triggerCode">
                    <el-input v-model="form.triggerCode" clearable />
                </el-form-item>
                <el-form-item label="对象类型" prop="objectTypeCode">
                    <el-input v-model="form.objectTypeCode" clearable />
                </el-form-item>
                <el-form-item label="对象 ID" prop="objectId">
                    <el-input-number v-model="form.objectId" :min="1" controls-position="right" />
                </el-form-item>
                <el-form-item label="规则类型" prop="ruleType">
                    <el-select v-model="form.ruleType" clearable style="width: 140px">
                        <el-option
                            v-for="value in ruleTypes"
                            :key="value"
                            :label="value"
                            :value="value"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="条数" prop="limit">
                    <el-input-number
                        v-model="form.limit"
                        :min="1"
                        :max="500"
                        controls-position="right"
                    />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" :loading="loading" @click="submitSearch"
                        >查询</el-button
                    >
                    <el-button @click="resetForm">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>
        <el-card shadow="never">
            <el-table v-loading="loading" :data="items" row-key="id">
                <el-table-column prop="createdAt" label="时间" width="170" />
                <el-table-column prop="schemeVersionId" label="治理版本" width="100" />
                <el-table-column prop="triggerCode" label="触发点" width="130" />
                <el-table-column prop="objectTypeCode" label="对象类型" width="130" />
                <el-table-column prop="objectId" label="对象 ID" width="100" />
                <el-table-column prop="ruleCode" label="规则" width="160" />
                <el-table-column prop="ruleType" label="类型" width="120" />
                <el-table-column label="结果" width="90">
                    <template #default="{ row }">
                        <el-tag
                            :type="
                                row.blockingFlag ? 'danger' : row.matchedFlag ? 'primary' : 'info'
                            "
                        >
                            {{ row.blockingFlag ? "阻断" : row.matchedFlag ? "命中" : "跳过" }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="message" label="消息" />
            </el-table>
        </el-card>
    </section>
</template>
