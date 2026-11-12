<script setup lang="ts">
import { Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";

import CursorPagination from "@/shared/components/CursorPagination.vue";
import { AmDataTable } from "@/shared/components/data-table";
import { listAccessTokenCalls } from "@/shared/api/service-account";
import type { AccessTokenCallDto } from "@/shared/types/service-account";
import { requestErrorMessage } from "@/shared/requestError";

const form = reactive({ serviceAccountId: "", tokenId: "" });
const filters = ref<{ serviceAccountId?: number; tokenId?: number }>({});
const calls = ref<AccessTokenCallDto[]>([]);
const loading = ref(false);
const limit = ref(100);
const cursor = ref<string | null>(null);
const total = ref<number | null>();
const prev = ref<string | null>();
const next = ref<string | null>();

const failureLabels: Record<string, string> = {
    INVALID: "令牌无效",
    EXPIRED: "已过期",
    REVOKED: "已吊销",
    DISABLED: "主体已停用",
    RATE_LIMITED: "超过限流",
};

function positiveId(value: string) {
    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

async function loadCalls() {
    loading.value = true;
    try {
        const response = await listAccessTokenCalls({
            ...filters.value,
            limit: limit.value,
            cursor: cursor.value ?? undefined,
        });
        calls.value = response.items;
        prev.value = response.prev;
        next.value = response.next;
        if (!cursor.value) total.value = response.total;
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "调用审计加载失败"));
    } finally {
        loading.value = false;
    }
}

function search() {
    filters.value = {
        serviceAccountId: positiveId(form.serviceAccountId),
        tokenId: positiveId(form.tokenId),
    };
    cursor.value = null;
    void loadCalls();
}

function reset() {
    form.serviceAccountId = "";
    form.tokenId = "";
    filters.value = {};
    cursor.value = null;
    void loadCalls();
}

function formatDateTime(value?: string | null) {
    return value ? value.replace("T", " ") : "-";
}

onMounted(loadCalls);
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>调用审计</h1>
            <el-text type="info">{{ total == null ? "" : `共 ${total} 条` }}</el-text>
        </div>
        <el-card class="am-page__filter" shadow="never">
            <el-form :inline="true" @submit.prevent="search">
                <el-form-item>
                    <el-input v-model="form.serviceAccountId" clearable placeholder="服务账号 ID" style="width: 160px" />
                </el-form-item>
                <el-form-item>
                    <el-input v-model="form.tokenId" clearable placeholder="令牌 ID" style="width: 160px" />
                </el-form-item>
                <el-form-item>
                    <el-button native-type="submit" type="primary" :icon="Search">查询</el-button>
                    <el-button @click="reset">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>
        <el-card class="am-page__result" shadow="never">
            <AmDataTable
                :data="calls"
                :loading="loading"
                row-key="id"
                sort-mode="none"
                :columns="[
                    { key: 'occurredAt', label: '时间', width: 180 },
                    { key: 'callerType', label: '调用方', width: 140 },
                    { key: 'serviceAccountId', label: '服务账号', width: 110 },
                    { key: 'tokenId', label: '令牌', width: 90 },
                    { key: 'httpMethod', label: '方法', width: 90 },
                    { key: 'requestPath', label: '路径', width: 220 },
                    { key: 'statusCode', label: '状态', width: 80 },
                    { key: 'durationMs', label: '耗时', width: 90 },
                    { key: 'sourceIp', label: '来源 IP', width: 140 },
                    { key: 'failureReason', label: '失败原因', width: 120 },
                    { key: 'traceId', label: 'Trace', width: 180 },
                ]"
            >
                <template #cell-occurredAt="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
                <template #cell-callerType="{ row }">
                    {{ row.callerType === "SERVICE_ACCOUNT" ? "服务账号" : row.callerType === "USER" ? "用户" : "未知" }}
                </template>
                <template #cell-serviceAccountId="{ row }">{{ row.serviceAccountId ?? "-" }}</template>
                <template #cell-tokenId="{ row }">{{ row.tokenPrefix || row.tokenId || "-" }}</template>
                <template #cell-durationMs="{ row }">{{ row.durationMs }} ms</template>
                <template #cell-sourceIp="{ row }">{{ row.sourceIp || "-" }}</template>
                <template #cell-failureReason="{ row }">
                    {{ row.failureReason ? (failureLabels[row.failureReason] ?? row.failureReason) : "-" }}
                </template>
                <template #cell-traceId="{ row }">{{ row.traceId || "-" }}</template>
            </AmDataTable>
            <div class="am-table-footer">
                <CursorPagination
                    :limit="limit"
                    :total="total ?? undefined"
                    :loading="loading"
                    :next="next"
                    :prev="prev"
                    @limit-change="
                        (value) => {
                            limit = value;
                            cursor = null;
                            loadCalls();
                        }
                    "
                    @page="
                        (value) => {
                            cursor = value;
                            loadCalls();
                        }
                    "
                />
            </div>
        </el-card>
    </section>
</template>
