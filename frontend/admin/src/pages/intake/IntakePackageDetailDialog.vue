<script setup lang="ts">
import { watch } from "vue";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import { AmDataTable } from "@/shared/components/data-table";
import {
    formatSize,
    generatedItemsEmptyDescription,
    statusLabel,
    statusType,
    validationCategoryLabel,
    validationOutcomeLabel,
    validationOutcomeType,
} from "./intakePackagePresentation";
import { useIntakePackageReview } from "./useIntakePackageReview";

const props = defineProps<{ packageId?: number; refreshHistory: () => Promise<void> }>();
const detailVisible = defineModel<boolean>({ required: true });
const {
    detailLoading,
    detailError,
    detail,
    detailId,
    reviewing,
    downloading,
    review,
    reviewCompleted,
    showDetail,
    acceptPackage,
    rejectPackage,
    downloadOriginalPackage,
} = useIntakePackageReview(() => props.refreshHistory());
watch(
    () => [detailVisible.value, props.packageId],
    () => {
        if (detailVisible.value && props.packageId) void showDetail(props.packageId);
    },
);
</script>

<template>
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
                    <el-descriptions-item v-if="detail.failureReason" label="失败原因" :span="2">
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
                    <AmDataTable
                        :data="detail.validations"
                        size="small"
                        :columns="[
                            { key: 'category', label: '类别', width: 90, sortable: true },
                            { key: 'outcome', label: '结果', width: 120, sortable: true },
                            { key: 'message', label: '检测说明', sortable: true },
                        ]"
                    >
                        <template #cell-category="{ row }">
                            {{ validationCategoryLabel(row.category) }}
                        </template>
                        <template #cell-outcome="{ row }">
                            <el-tag :type="validationOutcomeType(row.outcome)">
                                {{ validationOutcomeLabel(row.outcome) }}
                            </el-tag>
                        </template>
                    </AmDataTable>
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
                        <el-button type="danger" plain :disabled="reviewing" @click="rejectPackage">
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
                <AmDataTable
                    v-if="detail.generatedItems.length"
                    :data="detail.generatedItems"
                    class="generated-items"
                    size="small"
                    :columns="[
                        { key: 'archiveItemId', label: '档案 ID', width: 100, sortable: true },
                        { key: 'fondsCode', label: '全宗', width: 120, sortable: true },
                        { key: 'categoryCode', label: '分类', width: 120, sortable: true },
                        { key: 'archiveNo', label: '档号', sortable: true },
                        {
                            key: 'electronicFileCount',
                            label: '电子文件',
                            width: 100,
                            sortable: true,
                        },
                    ]"
                />
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
</template>

<style scoped>
.detail-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
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
</style>
