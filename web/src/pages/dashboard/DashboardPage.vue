<script setup lang="ts">
import { Check, Clock, Coin, MessageBox } from "@element-plus/icons-vue";

interface TodoItem {
    key: string;
    title: string;
    module: string;
    status: "待处理" | "处理中" | "已完成";
}

const todos: TodoItem[] = [
    { key: "1", title: "财务凭证归档批次待确认", module: "移交接收", status: "待处理" },
    { key: "2", title: "项目档案字段布局待发布", module: "目录配置", status: "处理中" },
    { key: "3", title: "历史系统同步目录已完成", module: "同步接入", status: "已完成" },
];

function statusType(status: TodoItem["status"]) {
    if (status === "已完成") return "success";
    if (status === "处理中") return "primary";
    return "warning";
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header"><h1>工作台</h1></div>
        <el-row :gutter="16">
            <el-col
                v-for="item in [
                    { title: '档案记录', value: 1280, icon: Coin },
                    { title: '待接收批次', value: 8, icon: MessageBox },
                    { title: '处理中任务', value: 16, icon: Clock },
                    { title: '今日完成', value: 42, icon: Check },
                ]"
                :key="item.title"
                :xs="24"
                :md="12"
                :xl="6"
                class="dashboard-stat"
            >
                <el-card shadow="never">
                    <el-statistic :title="item.title" :value="item.value">
                        <template #prefix
                            ><el-icon><component :is="item.icon" /></el-icon
                        ></template>
                    </el-statistic>
                </el-card>
            </el-col>
            <el-col :span="24">
                <el-card header="近期事项" shadow="never">
                    <el-table :data="todos" row-key="key">
                        <el-table-column label="事项" prop="title" />
                        <el-table-column label="模块" prop="module" width="120" />
                        <el-table-column label="状态" width="110">
                            <template #default="{ row }">
                                <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-card>
            </el-col>
        </el-row>
    </section>
</template>

<style scoped>
.dashboard-stat {
    margin-bottom: 16px;
}
</style>
