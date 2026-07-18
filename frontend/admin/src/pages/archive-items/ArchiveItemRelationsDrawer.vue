<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onBeforeUnmount, ref, watch } from "vue";

import { errorMessage } from "@archive-management/frontend-core/api";

import { listArchiveCategories } from "@/shared/api/archive-metadata";
import {
    createArchiveItemRelation,
    deleteArchiveItemRelation,
    discoverArchiveRecords,
    listArchiveItemRelations,
} from "@/shared/api/archive-records";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import type { ArchiveCategoryDto } from "@/shared/types/archive-metadata";
import type {
    ArchiveItemRelationResponse,
    ArchiveRecordListDto,
} from "@/shared/types/archive-records";
import type { CursorPageResponse } from "@/shared/types/pagination";

const props = defineProps<{
    archiveItemId: number;
    active: boolean;
    canUpdate: boolean;
}>();

const depth = 1;
const relations = ref<CursorPageResponse<ArchiveItemRelationResponse>>();
const relationLimit = ref(100);
const relationCursor = ref<string>();
const relationLoading = ref(false);
const relationError = ref<string>();
const categories = ref<ArchiveCategoryDto[]>([]);
const categoryLoading = ref(false);
const categoryError = ref<string>();
const targetCategoryId = ref<number>();
const keywordDraft = ref("");
const committedKeyword = ref<string>();
const candidates = ref<ArchiveRecordListDto>();
const candidateLimit = ref(100);
const candidateCursor = ref<string>();
const candidateLoading = ref(false);
const candidateError = ref<string>();
const selectedTargetId = ref<number>();
const creating = ref(false);
const deletingRelationId = ref<number>();
let relationRequestVersion = 0;
let categoryRequestVersion = 0;
let candidateRequestVersion = 0;
let commandRequestVersion = 0;
let disposed = false;

watch(
    () => [props.archiveItemId, props.active] as const,
    ([archiveItemId, active]) => {
        invalidateRequests();
        relations.value = undefined;
        relationCursor.value = undefined;
        relationError.value = undefined;
        categoryError.value = undefined;
        candidates.value = undefined;
        candidateCursor.value = undefined;
        candidateError.value = undefined;
        selectedTargetId.value = undefined;
        if (active && archiveItemId > 0) {
            void loadRelations(undefined);
            if (!categories.value.length) void loadCategories();
        }
    },
    { immediate: true },
);

watch(targetCategoryId, () => {
    candidateRequestVersion += 1;
    candidateLoading.value = false;
    candidates.value = undefined;
    candidateCursor.value = undefined;
    candidateError.value = undefined;
    selectedTargetId.value = undefined;
});

onBeforeUnmount(() => {
    disposed = true;
    invalidateRequests();
});

async function loadRelations(cursor = relationCursor.value) {
    if (!props.active) return;
    const archiveItemId = props.archiveItemId;
    const version = ++relationRequestVersion;
    relationCursor.value = cursor;
    relationLoading.value = true;
    relationError.value = undefined;
    try {
        const response = await listArchiveItemRelations(archiveItemId, {
            depth,
            limit: relationLimit.value,
            cursor,
        });
        if (version === relationRequestVersion && isCurrentItem(archiveItemId))
            relations.value = response;
    } catch (error) {
        if (version === relationRequestVersion && isCurrentItem(archiveItemId))
            relationError.value = errorMessage(error, "加载档案关系失败");
    } finally {
        if (version === relationRequestVersion) relationLoading.value = false;
    }
}

async function loadCategories() {
    const version = ++categoryRequestVersion;
    categoryLoading.value = true;
    categoryError.value = undefined;
    try {
        const response = await listArchiveCategories(true);
        if (version === categoryRequestVersion && !disposed)
            categories.value = response.items.filter((category) => category.enabled);
    } catch (error) {
        if (version === categoryRequestVersion && !disposed)
            categoryError.value = errorMessage(error, "加载档案分类失败");
    } finally {
        if (version === categoryRequestVersion) categoryLoading.value = false;
    }
}

async function loadCandidates(cursor = candidateCursor.value) {
    if (!props.active || !targetCategoryId.value) return;
    const archiveItemId = props.archiveItemId;
    const categoryId = targetCategoryId.value;
    const version = ++candidateRequestVersion;
    if (cursor !== candidateCursor.value) selectedTargetId.value = undefined;
    candidateCursor.value = cursor;
    candidateLoading.value = true;
    candidateError.value = undefined;
    try {
        const response = await discoverArchiveRecords({
            categoryId,
            keyword: committedKeyword.value,
            limit: candidateLimit.value,
            cursor,
        });
        if (
            version === candidateRequestVersion &&
            isCurrentItem(archiveItemId) &&
            targetCategoryId.value === categoryId
        ) {
            candidates.value = {
                ...response,
                items: response.items.filter((row) => Number(row.id) !== archiveItemId),
            };
        }
    } catch (error) {
        if (
            version === candidateRequestVersion &&
            isCurrentItem(archiveItemId) &&
            targetCategoryId.value === categoryId
        )
            candidateError.value = errorMessage(error, "搜索目标档案失败");
    } finally {
        if (version === candidateRequestVersion) candidateLoading.value = false;
    }
}

function searchCandidates() {
    if (!targetCategoryId.value) {
        ElMessage.warning("请先选择目标档案分类");
        return;
    }
    committedKeyword.value = keywordDraft.value.trim() || undefined;
    candidateCursor.value = undefined;
    selectedTargetId.value = undefined;
    void loadCandidates(undefined);
}

async function createRelation() {
    if (!props.canUpdate || !selectedTargetId.value || creating.value) return;
    const archiveItemId = props.archiveItemId;
    const targetItemId = selectedTargetId.value;
    const version = ++commandRequestVersion;
    creating.value = true;
    try {
        await createArchiveItemRelation(archiveItemId, targetItemId);
        if (version !== commandRequestVersion || !isCurrentItem(archiveItemId)) return;
        ElMessage.success("档案关系已创建");
        selectedTargetId.value = undefined;
        relationCursor.value = undefined;
        await loadRelations(undefined);
    } catch (error) {
        if (version === commandRequestVersion && isCurrentItem(archiveItemId))
            ElMessage.error(errorMessage(error, "创建档案关系失败"));
    } finally {
        if (version === commandRequestVersion) creating.value = false;
    }
}

async function removeRelation(value: unknown) {
    const relation = value as ArchiveItemRelationResponse;
    if (!props.canUpdate || deletingRelationId.value) return;
    const archiveItemId = props.archiveItemId;
    try {
        await ElMessageBox.confirm(
            `确认删除与 ${archiveNo(relation.relatedItem)} 的关系吗？`,
            "删除档案关系",
            { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" },
        );
    } catch {
        return;
    }
    if (!isCurrentItem(archiveItemId)) return;
    const version = ++commandRequestVersion;
    deletingRelationId.value = relation.id;
    try {
        await deleteArchiveItemRelation(archiveItemId, relation.id);
        if (version !== commandRequestVersion || !isCurrentItem(archiveItemId)) return;
        ElMessage.success("档案关系已删除");
        await loadRelations(relationCursor.value);
    } catch (error) {
        if (version === commandRequestVersion && isCurrentItem(archiveItemId))
            ElMessage.error(errorMessage(error, "删除档案关系失败"));
    } finally {
        if (version === commandRequestVersion) deletingRelationId.value = undefined;
    }
}

function changeRelationLimit(limit: number) {
    relationLimit.value = limit;
    relationCursor.value = undefined;
    void loadRelations(undefined);
}

function changeCandidateLimit(limit: number) {
    candidateLimit.value = limit;
    candidateCursor.value = undefined;
    selectedTargetId.value = undefined;
    void loadCandidates(undefined);
}

function invalidateRequests() {
    relationRequestVersion += 1;
    categoryRequestVersion += 1;
    candidateRequestVersion += 1;
    commandRequestVersion += 1;
    relationLoading.value = false;
    categoryLoading.value = false;
    candidateLoading.value = false;
    creating.value = false;
    deletingRelationId.value = undefined;
}

function isCurrentItem(archiveItemId: number) {
    return !disposed && props.active && props.archiveItemId === archiveItemId;
}

function archiveNo(value: unknown) {
    const row = value as Record<string, unknown>;
    return String(row.archiveNo ?? `档案 ${row.itemId ?? row.id}`);
}
</script>

<template>
    <section class="relation-workspace" aria-label="档案关系">
        <el-alert
            v-if="relationError"
            :title="relationError"
            type="error"
            show-icon
            :closable="false"
        >
            <el-button link :loading="relationLoading" @click="loadRelations()">重试</el-button>
        </el-alert>
        <el-table v-loading="relationLoading" :data="relations?.items || []" row-key="id">
            <el-table-column label="方向" width="100">
                <template #default="{ row }">
                    {{ row.direction === "OUTGOING" ? "关联到" : "关联自" }}
                </template>
            </el-table-column>
            <el-table-column label="档号">
                <template #default="{ row }">{{ archiveNo(row.relatedItem) }}</template>
            </el-table-column>
            <el-table-column label="分类" prop="relatedItem.categoryName" width="160" />
            <el-table-column label="全宗" prop="relatedItem.fondsName" width="160" />
            <el-table-column label="创建时间" prop="createdAt" width="180" />
            <el-table-column v-if="canUpdate" label="操作" width="90">
                <template #default="{ row }">
                    <el-button
                        link
                        type="danger"
                        :loading="deletingRelationId === row.id"
                        :disabled="Boolean(deletingRelationId)"
                        @click="removeRelation(row)"
                    >
                        删除
                    </el-button>
                </template>
            </el-table-column>
        </el-table>
        <CursorPagination
            :limit="relationLimit"
            :total="relations?.total"
            :prev="relations?.prev"
            :next="relations?.next"
            :loading="relationLoading"
            @page="loadRelations"
            @limit-change="changeRelationLimit"
        />

        <template v-if="canUpdate">
            <el-divider />
            <div class="candidate-toolbar">
                <el-select
                    v-model="targetCategoryId"
                    aria-label="目标档案分类"
                    :loading="categoryLoading"
                    clearable
                    placeholder="选择分类"
                >
                    <el-option
                        v-for="category in categories"
                        :key="category.id"
                        :label="category.categoryName"
                        :value="category.id"
                    />
                </el-select>
                <el-input
                    v-model="keywordDraft"
                    aria-label="目标档案关键词"
                    clearable
                    placeholder="档号或关键词"
                    @keyup.enter="searchCandidates"
                />
                <el-button :loading="candidateLoading" @click="searchCandidates">
                    搜索目标档案
                </el-button>
                <el-button
                    type="primary"
                    :loading="creating"
                    :disabled="!selectedTargetId || creating"
                    @click="createRelation"
                >
                    确认关联
                </el-button>
            </div>
            <el-alert
                v-if="categoryError"
                :title="categoryError"
                type="error"
                show-icon
                :closable="false"
            >
                <el-button link :loading="categoryLoading" @click="loadCategories">
                    重试加载分类
                </el-button>
            </el-alert>
            <el-alert
                v-if="candidateError"
                :title="candidateError"
                type="error"
                show-icon
                :closable="false"
            >
                <el-button link :loading="candidateLoading" @click="loadCandidates()">
                    重试
                </el-button>
            </el-alert>
            <el-radio-group
                v-loading="candidateLoading"
                v-model="selectedTargetId"
                class="candidate-list"
                aria-label="目标档案候选"
            >
                <el-radio
                    v-for="row in candidates?.items || []"
                    :key="Number(row.id)"
                    :value="Number(row.id)"
                    :aria-label="`选择档案 ${archiveNo(row)}`"
                    border
                >
                    <span>{{ archiveNo(row) }}</span>
                    <span>{{ row.fondsName || row.fonds_name || "-" }}</span>
                </el-radio>
            </el-radio-group>
            <CursorPagination
                v-if="candidates"
                :limit="candidateLimit"
                :total="candidates?.total"
                :prev="candidates.prev"
                :next="candidates.next"
                :loading="candidateLoading"
                @page="loadCandidates"
                @limit-change="changeCandidateLimit"
            />
        </template>
    </section>
</template>

<style scoped>
.relation-workspace,
.candidate-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.candidate-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
}

.candidate-toolbar .el-select {
    width: 180px;
}

.candidate-toolbar .el-input {
    width: 240px;
}

.candidate-list {
    align-items: stretch;
}

.candidate-list span + span {
    margin-left: 24px;
    color: var(--el-text-color-secondary);
}
</style>
