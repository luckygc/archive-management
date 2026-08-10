<script setup lang="ts">
import { deleteLoginSession, listLoginSessions } from "@archive-management/frontend-core/api";
import type { LoginSessionDto } from "@archive-management/frontend-core/types";
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, ref } from "vue";

import CursorPagination from "@/shared/components/CursorPagination.vue";
import { AmDataTable } from "@/shared/components/data-table";

const limit = ref(100);
const cursor = ref<string | null>(null);
const sessions = ref<LoginSessionDto[]>([]);
const total = ref<number | null>();
const prev = ref<string | null>();
const next = ref<string | null>();
const loading = ref(false);
const deletingId = ref<string>();

async function loadSessions() {
    loading.value = true;
    try {
        const response = await listLoginSessions({
            limit: limit.value,
            cursor: cursor.value,
            requestTotal: !cursor.value,
        });
        sessions.value = response.items;
        prev.value = response.prev;
        next.value = response.next;
        if (!cursor.value) total.value = response.total;
    } finally {
        loading.value = false;
    }
}

async function kickout(value: unknown) {
    const row = value as LoginSessionDto;
    try {
        await ElMessageBox.confirm("踢下线该登录会话？", "确认操作", {
            confirmButtonText: "踢下线",
            cancelButtonText: "取消",
            confirmButtonClass: "el-button--danger",
            type: "warning",
        });
    } catch {
        return;
    }
    deletingId.value = row.sessionId;
    try {
        await deleteLoginSession(row.sessionId);
        await loadSessions();
        ElMessage.success("登录会话已踢下线");
    } finally {
        deletingId.value = undefined;
    }
}

function changeLimit(value: number) {
    limit.value = value;
    cursor.value = null;
    void loadSessions();
}

function changePage(value: string) {
    cursor.value = value;
    void loadSessions();
}

function clientSummary(value: unknown) {
    const row = value as LoginSessionDto;
    return (
        [row.client.browserName, row.client.osName, row.client.deviceType]
            .filter(Boolean)
            .join(" / ") || "-"
    );
}

function formatDateTime(value?: string | null) {
    return value ? value.replace("T", " ") : "-";
}

onMounted(loadSessions);
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>登录会话</h1>
            <el-text type="info">{{ total == null ? "" : `共 ${total} 条` }}</el-text>
        </div>
        <el-card shadow="never">
            <AmDataTable
                :data="sessions"
                :loading="loading"
                row-key="sessionId"
                sort-mode="none"
                :columns="[
                    { key: 'displayName', label: '用户', width: 180 },
                    { key: 'username', label: '账号', width: 160 },
                    { key: 'client', label: '客户端', width: 260 },
                    { key: 'request', label: '请求', width: 220 },
                    { key: 'lastAccessTime', label: '最后访问', width: 180 },
                    { key: 'expiresAt', label: '过期时间', width: 180 },
                    { key: 'actions', label: '操作', width: 110, fixed: 'right' },
                ]"
            >
                <template #cell-displayName="{ row }">
                    <div class="cell-inline">
                        <span>{{ row.displayName || "-" }}</span>
                        <el-tag v-if="row.current" type="primary">当前</el-tag>
                    </div>
                </template>
                <template #cell-client="{ row }">
                    <div>{{ clientSummary(row) }}</div>
                    <el-text type="info">{{ row.client.userAgent || "-" }}</el-text>
                </template>
                <template #cell-request="{ row }">
                    <div>{{ row.request.remoteAddress || "-" }}</div>
                    <el-text type="info">{{ row.request.host || "-" }}</el-text>
                </template>
                <template #cell-lastAccessTime="{ row }">
                    {{ formatDateTime(row.lastAccessTime) }}
                </template>
                <template #cell-expiresAt="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
                <template #cell-actions="{ row }">
                    <el-button v-if="row.current" disabled size="small">当前会话</el-button>
                    <el-button
                        v-else
                        type="danger"
                        plain
                        size="small"
                        :loading="deletingId === row.sessionId"
                        @click="kickout(row)"
                        >踢下线</el-button
                    >
                </template>
            </AmDataTable>
            <div class="am-table-footer">
                <CursorPagination
                    :limit="limit"
                    :total="total ?? undefined"
                    :loading="loading"
                    :next="next"
                    :prev="prev"
                    @limit-change="changeLimit"
                    @page="changePage"
                />
            </div>
        </el-card>
    </section>
</template>

<style scoped>
.cell-inline {
    display: flex;
    align-items: center;
    gap: 6px;
}
</style>
