<script setup lang="ts">
import { listAuthenticationEvents } from "@archive-management/frontend-core/api";
import type {
    AuthenticationEventDto,
    ListAuthenticationEventsParams,
} from "@archive-management/frontend-core/types";
import { Search } from "@element-plus/icons-vue";
import { onMounted, reactive, ref } from "vue";

import CursorPagination from "@/shared/components/CursorPagination.vue";

const eventTypeOptions = [
    { label: "登录成功", value: "login_success" },
    { label: "登录失败", value: "login_failure" },
    { label: "主动退出", value: "logout" },
    { label: "踢下线", value: "kickout" },
];

const form = reactive<{
    eventType?: string;
    username: string;
    keyword: string;
    occurredRange?: [string, string];
}>({ username: "", keyword: "" });
const filters = ref<ListAuthenticationEventsParams>({});
const limit = ref(100);
const cursor = ref<string | null>(null);
const events = ref<AuthenticationEventDto[]>([]);
const total = ref<number | null>();
const prev = ref<string | null>();
const next = ref<string | null>();
const loading = ref(false);

async function loadEvents() {
    loading.value = true;
    try {
        const response = await listAuthenticationEvents({
            ...filters.value,
            limit: limit.value,
            cursor: cursor.value,
            requestTotal: !cursor.value,
        });
        events.value = response.items;
        prev.value = response.prev;
        next.value = response.next;
        if (!cursor.value) total.value = response.total;
    } finally {
        loading.value = false;
    }
}

function search() {
    filters.value = {
        eventType: form.eventType,
        username: form.username.trim() || undefined,
        keyword: form.keyword.trim() || undefined,
        occurredAfter: form.occurredRange?.[0],
        occurredBefore: form.occurredRange?.[1],
    };
    cursor.value = null;
    void loadEvents();
}

function reset() {
    form.eventType = undefined;
    form.username = "";
    form.keyword = "";
    form.occurredRange = undefined;
    filters.value = {};
    cursor.value = null;
    void loadEvents();
}

function changeLimit(value: number) {
    limit.value = value;
    cursor.value = null;
    void loadEvents();
}
function changePage(value: string) {
    cursor.value = value;
    void loadEvents();
}
function eventLabel(type: string) {
    return eventTypeOptions.find((option) => option.value === type)?.label ?? type;
}
function eventTagType(type: string) {
    return type === "login_success"
        ? "success"
        : type === "login_failure"
          ? "danger"
          : type === "kickout"
            ? "warning"
            : "info";
}
function clientSummary(value: unknown) {
    const row = value as AuthenticationEventDto;
    return (
        [row.client.browserName, row.client.osName, row.client.deviceType]
            .filter(Boolean)
            .join(" / ") || "-"
    );
}
function formatDateTime(value?: string | null) {
    return value ? value.replace("T", " ") : "-";
}

onMounted(loadEvents);
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>认证审计</h1>
            <el-text type="info">{{ total == null ? "" : `共 ${total} 条` }}</el-text>
        </div>
        <el-card class="am-page__filter" shadow="never">
            <el-form :inline="true" :model="form" @submit.prevent="search">
                <el-form-item
                    ><el-select
                        v-model="form.eventType"
                        clearable
                        placeholder="事件类型"
                        style="width: 140px"
                        ><el-option
                            v-for="option in eventTypeOptions"
                            :key="option.value"
                            :label="option.label"
                            :value="option.value" /></el-select
                ></el-form-item>
                <el-form-item
                    ><el-input
                        v-model="form.username"
                        clearable
                        placeholder="账号"
                        style="width: 160px"
                /></el-form-item>
                <el-form-item
                    ><el-input
                        v-model="form.keyword"
                        clearable
                        placeholder="关键字"
                        style="width: 220px"
                /></el-form-item>
                <el-form-item
                    ><el-date-picker
                        v-model="form.occurredRange"
                        type="datetimerange"
                        value-format="YYYY-MM-DDTHH:mm:ss"
                        start-placeholder="开始时间"
                        end-placeholder="结束时间"
                /></el-form-item>
                <el-form-item
                    ><el-button native-type="submit" type="primary" :icon="Search">查询</el-button
                    ><el-button @click="reset">重置</el-button></el-form-item
                >
            </el-form>
        </el-card>
        <el-card class="am-page__result" shadow="never">
            <el-table v-loading="loading" :data="events" row-key="id">
                <el-table-column label="事件" width="120"
                    ><template #default="{ row }"
                        ><el-tag :type="eventTagType(row.eventType)">{{
                            eventLabel(row.eventType)
                        }}</el-tag></template
                    ></el-table-column
                >
                <el-table-column label="账号" prop="username" width="150" />
                <el-table-column label="用户" prop="displayName" width="160" />
                <el-table-column label="操作人" width="140"
                    ><template #default="{ row }">{{
                        row.operatorUsername || "-"
                    }}</template></el-table-column
                >
                <el-table-column label="客户端" width="240"
                    ><template #default="{ row }"
                        ><div>{{ clientSummary(row) }}</div>
                        <el-text type="info">{{ row.client.userAgent || "-" }}</el-text></template
                    ></el-table-column
                >
                <el-table-column label="请求" width="220"
                    ><template #default="{ row }"
                        ><div>{{ row.request.remoteAddress || "-" }}</div>
                        <el-text type="info">{{ row.request.host || "-" }}</el-text></template
                    ></el-table-column
                >
                <el-table-column label="失败原因" width="220"
                    ><template #default="{ row }">{{
                        row.failureReason || "-"
                    }}</template></el-table-column
                >
                <el-table-column label="发生时间" width="180"
                    ><template #default="{ row }">{{
                        formatDateTime(row.occurredAt)
                    }}</template></el-table-column
                >
            </el-table>
            <div class="am-table-footer">
                <CursorPagination
                    :limit="limit"
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
