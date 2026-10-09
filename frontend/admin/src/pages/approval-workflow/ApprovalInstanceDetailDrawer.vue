<script setup lang="ts">
import { AmDataTable } from "@/shared/components/data-table";
import type { ApprovalWorkflowInstanceDetailDto } from "@/shared/types/approval-workflow";
import {
    actionLabel,
    formatTime,
    instanceStatusLabel,
    instanceTagType,
    taskStatusLabel,
} from "./approvalPresentation";

defineProps<{ detail?: ApprovalWorkflowInstanceDetailDto; loading: boolean }>();
const detailOpen = defineModel<boolean>({ required: true });
</script>

<template>
    <el-drawer v-model="detailOpen" title="审批详情" size="760px">
        <div v-loading="loading">
            <template v-if="detail">
                <el-descriptions :column="2" border>
                    <el-descriptions-item label="标题">{{
                        detail.instance.title
                    }}</el-descriptions-item>
                    <el-descriptions-item label="状态">
                        <el-tag :type="instanceTagType(detail.instance.status)">{{
                            instanceStatusLabel(detail.instance.status)
                        }}</el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="业务类型">{{
                        detail.instance.businessType
                    }}</el-descriptions-item>
                    <el-descriptions-item label="业务标识">{{
                        detail.instance.businessId
                    }}</el-descriptions-item>
                    <el-descriptions-item label="发起时间">{{
                        formatTime(detail.instance.createdAt)
                    }}</el-descriptions-item>
                    <el-descriptions-item label="完成时间">{{
                        formatTime(detail.instance.completedAt)
                    }}</el-descriptions-item>
                </el-descriptions>
                <h3>流程节点</h3>
                <AmDataTable
                    :data="detail.tasks"
                    row-key="id"
                    :bordered="true"
                    :columns="[
                        { key: 'nodeName', label: '节点', sortable: true },
                        { key: 'status', label: '状态', width: 110, sortable: true },
                        { key: 'createdAt', label: '到达时间', width: 180, sortable: true },
                        { key: 'completedAt', label: '完成时间', width: 180, sortable: true },
                    ]"
                >
                    <template #cell-status="{ row }">{{ taskStatusLabel(row.status) }}</template>
                    <template #cell-createdAt="{ row }">{{ formatTime(row.createdAt) }}</template>
                    <template #cell-completedAt="{ row }">
                        {{ formatTime(row.completedAt) }}
                    </template>
                </AmDataTable>
                <h3>审批意见</h3>
                <el-empty v-if="detail.opinions.length === 0" description="暂无审批意见" />
                <AmDataTable
                    v-else
                    :data="detail.opinions"
                    row-key="id"
                    :bordered="true"
                    :columns="[
                        { key: 'action', label: '动作', width: 100, sortable: true },
                        {
                            key: 'operatorUserId',
                            label: '办理人 ID',
                            width: 110,
                            sortable: true,
                        },
                        { key: 'comment', label: '意见', sortable: true },
                        { key: 'createdAt', label: '时间', width: 180, sortable: true },
                    ]"
                >
                    <template #cell-action="{ row }">{{ actionLabel(row.action) }}</template>
                    <template #cell-createdAt="{ row }">{{ formatTime(row.createdAt) }}</template>
                </AmDataTable>
            </template>
        </div>
    </el-drawer>
</template>

<style scoped>
h3 {
    margin: 20px 0 12px;
    font-size: 16px;
}
</style>
