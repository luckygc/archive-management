<script setup lang="ts">
import { UploadFilled } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref } from "vue";

import {
    acceptArchiveIntakePackage,
    downloadArchiveIntakePackage,
    getArchiveIntakePackage,
    listArchiveIntakePackages,
    receiveArchiveIntakePackage,
    rejectArchiveIntakePackage,
} from "@/shared/api/intake";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import { requestErrorMessage } from "@/shared/requestError";
import type {
    ArchiveIntakePackageDetailResponse,
    ArchiveIntakePackageListItemResponse,
    ArchiveIntakePackageStatus,
    ArchiveIntakeValidationCategory,
    ArchiveIntakeValidationOutcome,
} from "@/shared/types/intake";
import type { CursorPageResponse } from "@/shared/types/pagination";
import { usePermissionStore } from "@/stores/permissionStore";

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
const detailLoading = ref(false);
const detailError = ref<string>();
const detail = ref<ArchiveIntakePackageDetailResponse>();
const detailId = ref<number>();
const reviewing = ref(false);
const downloading = ref(false);
const review = ref(emptyReview());

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

async function showDetail(id: number) {
    detailId.value = id;
    detailVisible.value = true;
    detailLoading.value = true;
    detailError.value = undefined;
    detail.value = undefined;
    try {
        detail.value = await getArchiveIntakePackage(id);
        review.value = {
            sourceFixityConfirmed: detail.value.sourceFixityConfirmed,
            contentReadabilityConfirmed: detail.value.contentReadabilityConfirmed,
            antivirusPassed: detail.value.antivirusPassed,
            carrierSafetyConfirmed: detail.value.carrierSafetyConfirmed,
            handoverCompleted: detail.value.handoverCompleted,
            remark: detail.value.reviewRemark ?? "",
        };
    } catch (error) {
        detailError.value = requestErrorMessage(error, "接收结果加载失败");
    } finally {
        detailLoading.value = false;
    }
}

function statusType(status: ArchiveIntakePackageStatus) {
    if (status === "ACCEPTED") return "success";
    if (status === "FAILED" || status === "REJECTED") return "danger";
    if (status === "CHECKING" || status === "ACCEPTING" || status === "PENDING_REVIEW")
        return "warning";
    return "info";
}

function statusLabel(status: ArchiveIntakePackageStatus) {
    return {
        RECEIVED: "已接收",
        CHECKING: "自动检测中",
        PENDING_REVIEW: "待人工复核",
        ACCEPTING: "接收入库中",
        ACCEPTED: "已接收入馆藏",
        REJECTED: "已退回",
        FAILED: "自动检测失败",
    }[status];
}

function validationCategoryLabel(category: ArchiveIntakeValidationCategory) {
    return {
        AUTHENTICITY: "真实性",
        INTEGRITY: "完整性",
        USABILITY: "可用性",
        SECURITY: "安全性",
    }[category];
}

function validationOutcomeLabel(outcome: ArchiveIntakeValidationOutcome) {
    return {
        PASSED: "通过",
        WARNING: "提示",
        MANUAL_REVIEW: "需人工确认",
    }[outcome];
}

function validationOutcomeType(outcome: ArchiveIntakeValidationOutcome) {
    if (outcome === "PASSED") return "success";
    return "warning";
}

const reviewCompleted = computed(
    () =>
        review.value.sourceFixityConfirmed &&
        review.value.contentReadabilityConfirmed &&
        review.value.antivirusPassed &&
        review.value.carrierSafetyConfirmed &&
        review.value.handoverCompleted,
);

async function acceptPackage() {
    if (!detail.value || !reviewCompleted.value || reviewing.value) return;
    reviewing.value = true;
    try {
        detail.value = await acceptArchiveIntakePackage(detail.value.id, review.value);
        ElMessage.success(`已接收 ${detail.value.itemCount} 件档案并写入正式馆藏`);
        await load(cursor.value);
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "档案信息包验收失败"));
        if (detailId.value) await showDetail(detailId.value);
    } finally {
        reviewing.value = false;
    }
}

async function rejectPackage() {
    if (!detail.value || reviewing.value) return;
    try {
        const { value } = await ElMessageBox.prompt(
            "请输入退回原因，接收记录和原始信息包仍将保留",
            "退回档案信息包",
            {
                confirmButtonText: "确认退回",
                cancelButtonText: "取消",
                inputType: "textarea",
                inputValidator: (reason) => Boolean(reason.trim()) || "请输入退回原因",
                type: "warning",
            },
        );
        reviewing.value = true;
        detail.value = await rejectArchiveIntakePackage(detail.value.id, value.trim());
        ElMessage.success("档案信息包已退回");
        await load(cursor.value);
    } catch (error) {
        if (error !== "cancel" && error !== "close") {
            ElMessage.error(requestErrorMessage(error, "档案信息包退回失败"));
        }
    } finally {
        reviewing.value = false;
    }
}

async function downloadOriginalPackage() {
    if (!detail.value || downloading.value) return;
    downloading.value = true;
    try {
        await downloadArchiveIntakePackage(detail.value.id);
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "原始信息包下载失败"));
    } finally {
        downloading.value = false;
    }
}

function formatSize(size: number) {
    return size < 1024
        ? `${size} B`
        : size < 1024 * 1024
          ? `${(size / 1024).toFixed(1)} KB`
          : `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function formatTime(value?: string) {
    return value ? value.replace("T", " ").slice(0, 19) : "—";
}

function generatedItemsEmptyDescription(status: ArchiveIntakePackageStatus) {
    return status === "PENDING_REVIEW" ? "完成验收后生成正式馆藏档案" : "未生成正式馆藏档案";
}

function emptyReview() {
    return {
        sourceFixityConfirmed: false,
        contentReadabilityConfirmed: false,
        antivirusPassed: false,
        carrierSafetyConfirmed: false,
        handoverCompleted: false,
        remark: "",
    };
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

        <el-table
            v-if="result?.items.length || loading"
            v-loading="loading"
            :data="result?.items ?? []"
            row-key="id"
        >
            <el-table-column label="接收时间" width="170">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="信息包" min-width="190">
                <template #default="{ row }">
                    <div>{{ row.originalFileName }}</div>
                    <div class="secondary-text">
                        {{ row.packageCode || "未解析包编码" }} ·
                        {{ formatSize(row.contentLength) }}
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
                <template #default="{ row }">
                    <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="档案/电子文件" width="140">
                <template #default="{ row }">
                    {{ row.itemCount }} 件 / {{ row.electronicFileCount }} 个
                </template>
            </el-table-column>
            <el-table-column label="处理结果" min-width="220">
                <template #default="{ row }">
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
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                    <el-button link type="primary" @click="showDetail(row.id)">查看结果</el-button>
                </template>
            </el-table-column>
        </el-table>
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

        <el-dialog v-model="detailVisible" title="档案信息包接收详情" width="860px">
            <div v-loading="detailLoading" class="detail-body">
                <RequestErrorState
                    v-if="detailError"
                    :message="detailError"
                    :retrying="detailLoading"
                    @retry="detailId && showDetail(detailId)"
                />
                <template v-else-if="detail">
                    <el-descriptions :column="2" size="small" border>
                        <el-descriptions-item label="信息包编码">
                            {{ detail.packageCode || "未解析" }}
                        </el-descriptions-item>
                        <el-descriptions-item label="状态">
                            <el-tag :type="statusType(detail.status)">
                                {{ statusLabel(detail.status) }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="原文件名">
                            {{ detail.originalFileName }}
                        </el-descriptions-item>
                        <el-descriptions-item label="格式配置">
                            {{ detail.formatProfile }}
                        </el-descriptions-item>
                        <el-descriptions-item label="文件大小">
                            {{ formatSize(detail.contentLength) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="档案与电子文件">
                            {{ detail.itemCount }} 件档案 /
                            {{ detail.electronicFileCount }} 个电子文件（{{
                                formatSize(detail.electronicFileBytes)
                            }}）
                        </el-descriptions-item>
                        <el-descriptions-item label="SHA-256" :span="2">
                            <code class="digest">{{ detail.sha256 }}</code>
                        </el-descriptions-item>
                        <el-descriptions-item
                            v-if="detail.failureReason"
                            label="失败原因"
                            :span="2"
                        >
                            <span class="failure-text">{{ detail.failureReason }}</span>
                        </el-descriptions-item>
                    </el-descriptions>
                    <section>
                        <h2>四性自动检测结果</h2>
                        <el-alert
                            title="自动检测不能替代人工验收"
                            description="来源固化、可信签名、内容逐件可读、病毒检测和离线载体安全需要结合移交现场或外部工具确认。"
                            type="info"
                            :closable="false"
                            show-icon
                        />
                        <el-table :data="detail.validations" size="small">
                            <el-table-column label="类别" width="90">
                                <template #default="{ row }">
                                    {{ validationCategoryLabel(row.category) }}
                                </template>
                            </el-table-column>
                            <el-table-column label="结果" width="120">
                                <template #default="{ row }">
                                    <el-tag :type="validationOutcomeType(row.outcome)">
                                        {{ validationOutcomeLabel(row.outcome) }}
                                    </el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column label="检测说明" prop="message" />
                        </el-table>
                    </section>
                    <section v-if="detail.status === 'PENDING_REVIEW'" class="review-panel">
                        <h2>人工复核与交接确认</h2>
                        <el-checkbox v-model="review.sourceFixityConfirmed">
                            已核验移交来源及固化信息
                        </el-checkbox>
                        <el-checkbox v-model="review.contentReadabilityConfirmed">
                            已抽查或逐件确认内容可正常打开、读取
                        </el-checkbox>
                        <el-checkbox v-model="review.antivirusPassed">
                            已使用受控环境完成病毒和恶意代码检测
                        </el-checkbox>
                        <el-checkbox v-model="review.carrierSafetyConfirmed">
                            已确认离线载体及读取环境安全
                        </el-checkbox>
                        <el-checkbox v-model="review.handoverCompleted">
                            已核对移交清单并完成移交接收登记手续
                        </el-checkbox>
                        <el-input
                            v-model="review.remark"
                            type="textarea"
                            :rows="2"
                            maxlength="500"
                            show-word-limit
                            placeholder="复核说明（可选）"
                        />
                        <el-alert
                            v-if="!reviewCompleted"
                            title="完成全部人工确认后方可接收入正式馆藏"
                            type="warning"
                            :closable="false"
                            show-icon
                        />
                        <div class="review-actions">
                            <el-button
                                type="danger"
                                plain
                                :disabled="reviewing"
                                @click="rejectPackage"
                            >
                                退回
                            </el-button>
                            <el-button
                                type="primary"
                                :disabled="!reviewCompleted"
                                :loading="reviewing"
                                @click="acceptPackage"
                            >
                                确认接收入正式馆藏
                            </el-button>
                        </div>
                    </section>
                    <el-table
                        v-if="detail.generatedItems.length"
                        :data="detail.generatedItems"
                        class="generated-items"
                        size="small"
                    >
                        <el-table-column label="档案 ID" prop="archiveItemId" width="100" />
                        <el-table-column label="全宗" prop="fondsCode" width="120" />
                        <el-table-column label="分类" prop="categoryCode" width="120" />
                        <el-table-column label="档号" prop="archiveNo" />
                        <el-table-column label="电子文件" prop="electronicFileCount" width="100" />
                    </el-table>
                    <el-empty
                        v-else
                        :description="generatedItemsEmptyDescription(detail.status)"
                        :image-size="72"
                    />
                    <div class="dialog-actions">
                        <el-button :loading="downloading" @click="downloadOriginalPackage">
                            下载原始信息包
                        </el-button>
                    </div>
                </template>
            </div>
        </el-dialog>
    </main>
</template>

<style scoped>
.intake-page,
.detail-body {
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
.digest {
    overflow-wrap: anywhere;
}
.generated-items {
    width: 100%;
}
.detail-body section {
    display: flex;
    flex-direction: column;
    gap: 12px;
}
.detail-body h2 {
    margin: 0;
    font-size: 16px;
}
.review-panel {
    padding: 16px;
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
    background: var(--el-fill-color-lighter);
}
.review-panel :deep(.el-checkbox) {
    height: auto;
    margin-right: 0;
    white-space: normal;
}
.review-actions,
.dialog-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
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
