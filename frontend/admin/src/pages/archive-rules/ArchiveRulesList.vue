<script setup lang="ts">
import { AmDataTable } from "@/shared/components/data-table";
import type {
    ArchiveRuntimeDefinitionDto,
    ArchiveRuntimeStatus,
    ArchiveRuntimeTriggerPoint,
} from "@/shared/types/archive-rules";
import { triggerLabels, triggerPoints } from "./archiveRuleForm";

defineProps<{ definitions: ArchiveRuntimeDefinitionDto[]; loading: boolean; loadError?: string }>();
const status = defineModel<ArchiveRuntimeStatus>("status");
const triggerPoint = defineModel<ArchiveRuntimeTriggerPoint>("triggerPoint");
const emit = defineEmits<{
    refresh: [];
    create: [];
    edit: [row: unknown];
    publish: [row: unknown];
    remove: [row: unknown];
    enable: [row: unknown, enabled: boolean];
}>();
</script>

<template>
    <div>
        <el-card class="runtime-filter" shadow="never">
            <div class="runtime-filter__grid">
                <label>
                    <span>状态</span>
                    <el-select v-model="status" clearable placeholder="全部状态">
                        <el-option label="草稿" value="DRAFT" />
                        <el-option label="已发布" value="PUBLISHED" />
                    </el-select>
                </label>
                <label>
                    <span>触发点</span>
                    <el-select
                        v-model="triggerPoint"
                        clearable
                        placeholder="全部触发点"
                        @change="emit('refresh')"
                    >
                        <el-option
                            v-for="item in triggerPoints"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                        />
                    </el-select>
                </label>
                <el-button :loading="loading" @click="emit('refresh')">刷新</el-button>
            </div>
        </el-card>
        <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />
        <el-card class="runtime-list" shadow="never">
            <el-empty v-if="!loading && definitions.length === 0" description="还没有运行时定义">
                <el-button type="primary" @click="emit('create')">创建第一条约束</el-button>
            </el-empty>
            <AmDataTable
                v-else
                :data="definitions"
                :loading="loading"
                row-key="id"
                size="small"
                :columns="[
                    { key: 'priority', label: '#', width: 60, sortable: true },
                    { key: 'definitionName', label: '定义', minWidth: 220, sortable: true },
                    { key: 'definitionKind', label: '类别', width: 100, sortable: true },
                    { key: 'triggerPoint', label: '触发点', minWidth: 170, sortable: true },
                    {
                        key: 'scopeCategoryCode',
                        label: '分类范围',
                        minWidth: 130,
                        sortable: true,
                    },
                    { key: 'actionsSummary', label: '动作', minWidth: 150 },
                    { key: 'status', label: '状态', width: 100, sortable: true },
                    { key: 'enabled', label: '启用', width: 78, sortable: true },
                    { key: 'actions', label: '操作', width: 190, fixed: 'right' },
                ]"
            >
                <template #cell-definitionName="{ row }">
                    <div class="definition-cell">
                        <strong>{{ row.definitionName }}</strong>
                        <code>{{ row.definitionCode }}</code>
                    </div>
                </template>
                <template #cell-definitionKind="{ row }">
                    <el-tag effect="plain">
                        {{ row.definitionKind === "CONSTRAINT" ? "约束" : "规则" }}
                    </el-tag>
                </template>
                <template #cell-triggerPoint="{ row }">{{
                    triggerLabels[row.triggerPoint]
                }}</template>
                <template #cell-scopeCategoryCode="{ row }">{{
                    row.scopeCategoryCode || "全部分类"
                }}</template>
                <template #cell-actionsSummary="{ row }">
                    <span v-if="row.definitionKind === 'CONSTRAINT'">{{
                        row.constraintAction
                    }}</span>
                    <el-tag
                        v-for="action in row.actions"
                        v-else
                        :key="action.id"
                        class="action-tag"
                        size="small"
                        effect="plain"
                        >{{ action.actionType }}</el-tag
                    >
                </template>
                <template #cell-status="{ row }">
                    <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
                        {{ row.status === "PUBLISHED" ? "已发布" : "草稿" }}
                    </el-tag>
                </template>
                <template #cell-enabled="{ row }">
                    <el-switch
                        :model-value="row.enabled"
                        :disabled="row.status !== 'PUBLISHED'"
                        @change="emit('enable', row, Boolean($event))"
                    />
                </template>
                <template #cell-actions="{ row }">
                    <el-button v-if="row.status === 'DRAFT'" link @click="emit('edit', row)"
                        >编辑</el-button
                    >
                    <el-button
                        v-if="row.status === 'DRAFT'"
                        link
                        type="primary"
                        @click="emit('publish', row)"
                        >发布</el-button
                    >
                    <el-button
                        v-if="row.status === 'DRAFT'"
                        link
                        type="danger"
                        @click="emit('remove', row)"
                        >删除</el-button
                    >
                    <span v-else class="immutable-label">语义已锁定</span>
                </template>
            </AmDataTable>
        </el-card>
    </div>
</template>

<style scoped>
.runtime-filter {
    margin-bottom: 14px;
}
.runtime-filter__grid {
    display: grid;
    grid-template-columns: minmax(180px, 240px) minmax(140px, 180px) minmax(220px, 1fr) auto;
    gap: 14px;
    align-items: end;
}
.runtime-filter__grid label {
    display: grid;
    gap: 7px;
    color: var(--runtime-muted);
    font-size: 12px;
}
.runtime-list :deep(.el-card__body) {
    padding: 0;
}
.definition-cell {
    display: grid;
    gap: 3px;
}
.definition-cell strong {
    color: var(--runtime-ink);
}
.definition-cell code {
    color: var(--runtime-muted);
    font-size: 11px;
}
.action-tag + .action-tag {
    margin-left: 4px;
}
.immutable-label {
    color: var(--runtime-muted);
    font-size: 12px;
}
@media (max-width: 900px) {
    .runtime-filter__grid {
        grid-template-columns: 1fr;
    }
}
</style>
