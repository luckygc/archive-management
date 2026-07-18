<script setup lang="ts">
import type { FormInstance } from "element-plus";
import { ref } from "vue";

import { searchArchiveRuntimeTraces } from "@/shared/api/archive-rules";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import { requestErrorMessage } from "@/shared/requestError";
import type {
    ArchiveRuntimeDefinitionKind,
    ArchiveRuntimeTraceDto,
    ArchiveRuntimeTriggerPoint,
    SearchArchiveRuntimeTracesRequest,
} from "@/shared/types/archive-rules";
import type { CursorPageResponse } from "@/shared/types/pagination";

const triggerPoints: ArchiveRuntimeTriggerPoint[] = [
    "ITEM_BEFORE_CREATE",
    "ITEM_BEFORE_UPDATE",
    "ITEM_BEFORE_DELETE",
    "VOLUME_BEFORE_CREATE",
    "VOLUME_BEFORE_ADD_ITEM",
    "FILE_BEFORE_UPLOAD",
    "EXPORT_BEFORE_CREATE",
];

const formRef = ref<FormInstance>();
const form = ref({
    schemeVersionId: undefined as number | undefined,
    triggerPoint: undefined as ArchiveRuntimeTriggerPoint | undefined,
    objectTypeCode: "",
    objectId: undefined as number | undefined,
    definitionKind: undefined as ArchiveRuntimeDefinitionKind | undefined,
});
const limit = ref(100);
const committedQuery = ref<SearchArchiveRuntimeTracesRequest>();
const result = ref<CursorPageResponse<ArchiveRuntimeTraceDto>>();
const loading = ref(false);
const loadError = ref<string>();
let submissionVersion = 0;
let requestVersion = 0;

async function submitSearch() {
    const version = ++submissionVersion;
    const query: SearchArchiveRuntimeTracesRequest = {
        schemeVersionId: form.value.schemeVersionId,
        triggerPoint: form.value.triggerPoint,
        objectTypeCode: form.value.objectTypeCode.trim() || undefined,
        objectId: form.value.objectId,
        definitionKind: form.value.definitionKind,
    };
    const valid = await formRef.value?.validate().catch(() => false);
    if (version !== submissionVersion || valid === false) return;
    committedQuery.value = query;
    await load(undefined);
}

async function load(cursor?: string) {
    if (!committedQuery.value) return;
    const version = ++requestVersion;
    loading.value = true;
    try {
        const response = await searchArchiveRuntimeTraces({
            ...committedQuery.value,
            limit: limit.value,
            cursor,
        });
        if (version !== requestVersion) return;
        result.value = response;
        loadError.value = undefined;
    } catch (error) {
        if (version !== requestVersion) return;
        loadError.value = requestErrorMessage(error, "规则追踪查询失败");
    } finally {
        if (version === requestVersion) loading.value = false;
    }
}

function page(cursor: string) {
    void load(cursor);
}

function limitChange(value: number) {
    limit.value = value;
    void load(undefined);
}

function resetForm() {
    submissionVersion += 1;
    formRef.value?.resetFields();
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header"><h1>运行时决策追踪</h1></div>
        <el-card class="am-page__filter" shadow="never">
            <el-form ref="formRef" :model="form" inline>
                <el-form-item label="治理版本" prop="schemeVersionId">
                    <el-input-number
                        v-model="form.schemeVersionId"
                        :min="1"
                        controls-position="right"
                    />
                </el-form-item>
                <el-form-item label="触发点" prop="triggerPoint">
                    <el-select v-model="form.triggerPoint" clearable style="width: 220px">
                        <el-option
                            v-for="value in triggerPoints"
                            :key="value"
                            :label="value"
                            :value="value"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="对象类型" prop="objectTypeCode">
                    <el-input v-model="form.objectTypeCode" clearable />
                </el-form-item>
                <el-form-item label="对象 ID" prop="objectId">
                    <el-input-number v-model="form.objectId" :min="1" controls-position="right" />
                </el-form-item>
                <el-form-item label="定义类型" prop="definitionKind">
                    <el-select v-model="form.definitionKind" clearable style="width: 140px">
                        <el-option label="约束" value="CONSTRAINT" />
                        <el-option label="规则" value="RULE" />
                    </el-select>
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
            <RequestErrorState
                v-if="loadError"
                :message="loadError"
                :retrying="loading"
                @retry="load(undefined)"
            />
            <el-table v-loading="loading" :data="result?.items || []" row-key="id">
                <el-table-column prop="createdAt" label="时间" width="170" />
                <el-table-column prop="schemeVersionId" label="治理版本" width="100" />
                <el-table-column prop="triggerPoint" label="触发点" width="190" />
                <el-table-column prop="objectTypeCode" label="对象类型" width="130" />
                <el-table-column prop="objectId" label="对象 ID" width="100" />
                <el-table-column prop="definitionCode" label="定义" width="160" />
                <el-table-column prop="definitionKind" label="类型" width="100" />
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
            <div v-if="result" class="am-table-footer">
                <CursorPagination
                    :limit="limit"
                    :total="result.total"
                    :prev="result.prev"
                    :next="result.next"
                    :loading="loading"
                    @limit-change="limitChange"
                    @page="page"
                />
            </div>
        </el-card>
    </section>
</template>
