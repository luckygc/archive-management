<script setup lang="ts">
import { UploadFilled } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { listArchiveIntakePackages, receiveArchiveIntakePackage } from "@/shared/api/intake";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import { AmDataTable } from "@/shared/components/data-table";
import { requestErrorMessage } from "@/shared/requestError";
import type { ArchiveIntakePackageListItemResponse } from "@/shared/types/intake";
import type { CursorPageResponse } from "@/shared/types/pagination";
import { usePermissionStore } from "@/stores/permissionStore";
import IntakePackageDetailDialog from "./IntakePackageDetailDialog.vue";
import { formatSize, formatTime, statusLabel, statusType } from "./intakePackagePresentation";

const MAX_PACKAGE_BYTES = 50 * 1024 * 1024;
const permissionStore = usePermissionStore();
const canCreate = computed(() => permissionStore.has("archive:item:create"));
const fileInput = ref<HTMLInputElement>();
const selectedFile = ref<File>();
const result = ref<CursorPageResponse<ArchiveIntakePackageListItemResponse>>();
const loading = ref(false);
const loadError = ref<string>();
const uploading = ref(false);
const limit = ref(100);
const cursor = ref<string>();
const detailVisible = ref(false);
const detailId = ref<number>();
onMounted(() => void load());

async function load(nextCursor?: string) {
    loading.value = true;
    loadError.value = undefined;
    cursor.value = nextCursor;
    try {
        result.value = await listArchiveIntakePackages({
            limit: limit.value,
            cursor: nextCursor,
        });
    } catch (error) {
        loadError.value = requestErrorMessage(error, "接收历史加载失败");
    } finally {
        loading.value = false;
    }
}

function selectFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".zip")) {
        ElMessage.error("请选择 ZIP 格式的档案信息包");
        input.value = "";
        return;
    }
    if (file.size > MAX_PACKAGE_BYTES) {
        ElMessage.error("档案信息包不能超过 50 MiB");
        input.value = "";
        return;
    }
    selectedFile.value = file;
}

async function receive() {
    if (!selectedFile.value || uploading.value) return;
    uploading.value = true;
    try {
        const received = await receiveArchiveIntakePackage(selectedFile.value);
        if (received.status === "PENDING_REVIEW") {
            ElMessage.success(`自动检测完成，共 ${received.itemCount} 件档案，等待人工复核交接`);
        } else {
            ElMessage.warning(received.failureReason ?? "信息包未通过自动检测");
        }
        selectedFile.value = undefined;
        if (fileInput.value) fileInput.value.value = "";
        await load();
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "档案信息包接收失败"));
    } finally {
        uploading.value = false;
    }
}

function page(nextCursor: string) {
    void load(nextCursor);
}

function limitChange(nextLimit: number) {
    limit.value = nextLimit;
    void load();
}

function showDetail(id: number) {
    detailId.value = id;
    detailVisible.value = true;
}
</script>

<template>
    <main class="am-page intake-page">
        <div class="am-page__header">
            <h1>归档接收</h1>
            <div class="intake-actions">
                <span v-if="selectedFile" class="selected-file">{{ selectedFile.name }}</span>
                <el-button :disabled="!canCreate || uploading" @click="fileInput?.click()">
                    选择信息包
                </el-button>
                <el-button
                    type="primary"
                    :icon="UploadFilled"
                    :disabled="!canCreate || !selectedFile"
                    :loading="uploading"
                    @click="receive"
                >
                    接收信息包
                </el-button>
                <input
                    ref="fileInput"
                    class="visually-hidden"
                    type="file"
                    accept=".zip,application/zip"
                    aria-label="选择档案信息包"
                    @change="selectFile"
                />
            </div>
        </div>

        <el-alert
            title="接收 DA/T 93—2022 件级离线电子档案移交信息包"
            description="系统保留原始 ZIP，自动检查目录结构、XML、文件关联、数量与摘要；自动检测后还需人工确认来源固化、内容可读、病毒检测、载体安全和交接手续，验收通过后直接写入正式馆藏，不进入预归档。单包最大 50 MiB。"
            type="info"
            :closable="false"
            show-icon
        />
        <el-alert
            v-if="!canCreate"
            title="当前账号可查看接收历史，但没有接收信息包权限"
            type="warning"
            :closable="false"
            show-icon
        />

        <RequestErrorState
            v-if="loadError"
            :message="loadError"
            :retrying="loading"
            @retry="load(cursor)"
        />

        <AmDataTable
            v-if="result?.items.length || loading"
            :data="result?.items ?? []"
            :loading="loading"
            row-key="id"
            sort-mode="none"
            :columns="[
                { key: 'createdAt', label: '接收时间', width: 170 },
                { key: 'package', label: '信息包', minWidth: 190 },
                { key: 'status', label: '状态', width: 100 },
                { key: 'counts', label: '档案/电子文件', width: 140 },
                { key: 'result', label: '处理结果', minWidth: 220 },
                { key: 'actions', label: '操作', width: 100, fixed: 'right' },
            ]"
        >
            <template #cell-createdAt="{ row }">{{ formatTime(row.createdAt) }}</template>
            <template #cell-package="{ row }">
                <div>{{ row.originalFileName }}</div>
                <div class="secondary-text">
                    {{ row.packageCode || "未解析包编码" }} ·
                    {{ formatSize(row.contentLength) }}
                </div>
            </template>
            <template #cell-status="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
            <template #cell-counts="{ row }">
                {{ row.itemCount }} 件 / {{ row.electronicFileCount }} 个
            </template>
            <template #cell-result="{ row }">
                <span
                    v-if="row.status === 'FAILED' || row.status === 'REJECTED'"
                    class="failure-text"
                >
                    {{ row.failureReason || "信息包未接收" }}
                </span>
                <span v-else>
                    自动通过 {{ row.validationPassedCount }} 项，人工
                    {{ row.validationManualCount }} 项
                </span>
            </template>
            <template #cell-actions="{ row }">
                <el-button link type="primary" @click="showDetail(row.id)">查看结果</el-button>
            </template>
        </AmDataTable>
        <el-empty v-else-if="!loadError" description="暂无接收记录，可选择 ZIP 信息包开始接收" />

        <CursorPagination
            v-if="result?.items.length"
            :limit="limit"
            :prev="result.prev"
            :next="result.next"
            :loading="loading"
            @page="page"
            @limit-change="limitChange"
        />

        <IntakePackageDetailDialog
            v-model="detailVisible"
            :package-id="detailId"
            :refresh-history="() => load(cursor)"
        />
    </main>
</template>

<style scoped>
.intake-page {
    display: flex;
    flex-direction: column;
    gap: 16px;
}
.intake-actions {
    display: flex;
    align-items: center;
    gap: 8px;
}
.selected-file {
    max-width: 260px;
    overflow: hidden;
    color: var(--el-text-color-regular);
    text-overflow: ellipsis;
    white-space: nowrap;
}
.secondary-text {
    margin-top: 2px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
}
.failure-text {
    color: var(--el-color-danger);
}
.visually-hidden {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
}
</style>
