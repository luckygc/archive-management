<script setup lang="ts">
import { ElMessage } from "element-plus";
import { ref, watch } from "vue";

import { errorMessage } from "@archive-management/frontend-core/api";

import { addArchiveItemToVolume } from "@/shared/api/archive-volumes";
import { searchArchiveRecords } from "@/shared/api/archive-records";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import type { ArchiveRecordListDto } from "@/shared/types/archive-records";
import type { ArchiveVolumeResponse } from "@/shared/types/archive-volumes";

const props = defineProps<{ volume?: ArchiveVolumeResponse; categoryId?: number }>();
const emit = defineEmits<{ close: [] }>();

const items = ref<ArchiveRecordListDto>();
const itemLimit = ref(100);
const itemCursor = ref<string>();
const itemLoading = ref(false);
const itemError = ref<string>();
const candidates = ref<ArchiveRecordListDto>();
const candidateLimit = ref(100);
const candidateCursor = ref<string>();
const candidateLoading = ref(false);
const candidateError = ref<string>();
const selectedItemId = ref<number>();
const adding = ref(false);
let itemRequestVersion = 0;
let candidateRequestVersion = 0;

watch(
    () => [props.volume?.id, props.categoryId] as const,
    ([volumeId, categoryId]) => {
        itemRequestVersion += 1;
        candidateRequestVersion += 1;
        items.value = undefined;
        candidates.value = undefined;
        selectedItemId.value = undefined;
        itemCursor.value = undefined;
        candidateCursor.value = undefined;
        if (volumeId && categoryId) {
            void loadItems();
            void loadCandidates();
        }
    },
    { immediate: true },
);

async function loadItems(nextCursor = itemCursor.value) {
    if (!props.volume || !props.categoryId) return;
    const version = ++itemRequestVersion;
    itemLoading.value = true;
    itemError.value = undefined;
    itemCursor.value = nextCursor;
    try {
        const response = await searchArchiveRecords({
            categoryId: props.categoryId,
            fondsCode: props.volume.fondsCode,
            volumeId: props.volume.id,
            limit: itemLimit.value,
            cursor: nextCursor,
        });
        if (version === itemRequestVersion) items.value = response;
    } catch (error) {
        if (version === itemRequestVersion)
            itemError.value = errorMessage(error, "加载卷内档案失败");
    } finally {
        if (version === itemRequestVersion) itemLoading.value = false;
    }
}

async function loadCandidates(nextCursor = candidateCursor.value) {
    if (!props.volume || !props.categoryId) return;
    const version = ++candidateRequestVersion;
    candidateLoading.value = true;
    candidateError.value = undefined;
    candidateCursor.value = nextCursor;
    try {
        const response = await searchArchiveRecords({
            categoryId: props.categoryId,
            fondsCode: props.volume.fondsCode,
            limit: candidateLimit.value,
            cursor: nextCursor,
        });
        if (version === candidateRequestVersion) candidates.value = response;
    } catch (error) {
        if (version === candidateRequestVersion)
            candidateError.value = errorMessage(error, "加载可加入档案失败");
    } finally {
        if (version === candidateRequestVersion) candidateLoading.value = false;
    }
}

async function addItem() {
    if (!props.volume || !selectedItemId.value || adding.value) return;
    adding.value = true;
    try {
        await addArchiveItemToVolume(props.volume.id, selectedItemId.value, undefined);
        ElMessage.success("档案已加入案卷");
        selectedItemId.value = undefined;
        itemCursor.value = undefined;
        await loadItems(undefined);
    } catch (error) {
        ElMessage.error(errorMessage(error, "加入案卷失败"));
    } finally {
        adding.value = false;
    }
}

function changeItemLimit(limit: number) {
    itemLimit.value = limit;
    itemCursor.value = undefined;
    void loadItems(undefined);
}

function changeCandidateLimit(limit: number) {
    candidateLimit.value = limit;
    candidateCursor.value = undefined;
    void loadCandidates(undefined);
}

function close() {
    itemRequestVersion += 1;
    candidateRequestVersion += 1;
    emit("close");
}

function archiveNo(row: Record<string, unknown>) {
    return String(row.archiveNo ?? row.archive_no ?? `档案 ${row.id}`);
}
</script>

<template>
    <el-drawer :model-value="Boolean(volume)" size="72%" @close="close">
        <template #header>
            <h2>{{ volume?.archiveNo || `案卷 ${volume?.id}` }} 卷内档案</h2>
        </template>
        <section class="volume-items-section" aria-label="卷内档案">
            <el-alert v-if="itemError" :title="itemError" type="error" show-icon :closable="false">
                <el-button link :loading="itemLoading" @click="loadItems()">重试</el-button>
            </el-alert>
            <el-table v-loading="itemLoading" :data="items?.items || []" row-key="id">
                <el-table-column label="档号">
                    <template #default="{ row }">{{ archiveNo(row) }}</template>
                </el-table-column>
                <el-table-column label="年度" prop="archiveYear" width="100" />
                <el-table-column label="电子状态" prop="electronicStatus" width="120" />
            </el-table>
            <CursorPagination
                :limit="itemLimit"
                :prev="items?.prev"
                :next="items?.next"
                :loading="itemLoading"
                @page="loadItems"
                @limit-change="changeItemLimit"
            />
        </section>
        <el-divider />
        <section class="volume-items-section" aria-label="可加入档案">
            <div class="volume-items-toolbar">
                <h3>选择档案加入案卷</h3>
                <el-button
                    type="primary"
                    :disabled="!selectedItemId"
                    :loading="adding"
                    @click="addItem"
                    >加入案卷</el-button
                >
            </div>
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
                v-model="selectedItemId"
                class="candidate-list"
                aria-label="候选档案"
            >
                <el-radio
                    v-for="row in candidates?.items || []"
                    :key="Number(row.id)"
                    :value="Number(row.id)"
                    :aria-label="`选择档案 ${archiveNo(row)}`"
                    border
                >
                    <span>{{ archiveNo(row) }}</span>
                    <span>{{ row.archiveYear || row.archive_year || "-" }}</span>
                    <span>{{ row.electronicStatus || row.electronic_status || "-" }}</span>
                </el-radio>
            </el-radio-group>
            <CursorPagination
                :limit="candidateLimit"
                :prev="candidates?.prev"
                :next="candidates?.next"
                :loading="candidateLoading"
                @page="loadCandidates"
                @limit-change="changeCandidateLimit"
            />
        </section>
    </el-drawer>
</template>

<style scoped>
.volume-items-section {
    display: flex;
    flex-direction: column;
    gap: 12px;
}
.volume-items-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
}
.volume-items-toolbar h3 {
    margin: 0;
}
.candidate-list {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
}
.candidate-list span + span {
    margin-left: 24px;
    color: var(--el-text-color-secondary);
}
</style>
