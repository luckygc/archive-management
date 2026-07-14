<script setup lang="ts">
import { Upload } from "@element-plus/icons-vue";

import type {
    ArchiveItemAuditDto,
    ArchiveItemElectronicFileDto,
} from "@/shared/types/archive-records";

defineProps<{
    state?: { archiveItemId: number; activeKey: "files" | "audits" };
    loading: boolean;
    fileForm: { usageType: string; displayOrder?: number };
    files: ArchiveItemElectronicFileDto[];
    audits: ArchiveItemAuditDto[];
    canCreateFile: boolean;
    canDeleteFile: boolean;
    canDownloadFile: boolean;
    uploading: boolean;
    downloadingFileId?: number;
    unbindingFileId?: number;
}>();

const emit = defineEmits<{
    close: [];
    tabChange: [value: string | number];
    upload: [event: Event];
    download: [id: number];
    unbind: [id: number];
}>();

function formatSize(size: number) {
    return size < 1024
        ? `${size} B`
        : size < 1024 * 1024
          ? `${(size / 1024).toFixed(1)} KB`
          : `${(size / 1024 / 1024).toFixed(1)} MB`;
}
</script>

<template>
    <el-drawer
        :model-value="Boolean(state)"
        v-loading="loading"
        :title="state ? `档案 ${state.archiveItemId}` : ''"
        size="70%"
        @close="emit('close')"
    >
        <el-tabs :model-value="state?.activeKey" @tab-change="emit('tabChange', $event)">
            <el-tab-pane label="电子文件" name="files">
                <div class="file-toolbar">
                    <el-input
                        v-model="fileForm.usageType"
                        :disabled="!canCreateFile"
                        placeholder="用途"
                    />
                    <el-input-number
                        v-model="fileForm.displayOrder"
                        :disabled="!canCreateFile"
                        placeholder="顺序"
                    />
                    <el-button
                        type="primary"
                        :disabled="!canCreateFile || uploading"
                        :loading="uploading"
                        :icon="Upload"
                        @click="($refs.uploadInput as HTMLInputElement).click()"
                        >上传附件</el-button
                    >
                    <input ref="uploadInput" hidden type="file" @change="emit('upload', $event)" />
                </div>
                <el-table :data="files" row-key="id">
                    <el-table-column label="文件名" prop="originalFilename" />
                    <el-table-column label="大小" width="100">
                        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
                    </el-table-column>
                    <el-table-column label="用途" prop="usageType" width="100" />
                    <el-table-column label="操作" width="140">
                        <template #default="{ row }">
                            <el-button
                                link
                                :disabled="!canDownloadFile || downloadingFileId === row.id"
                                :loading="downloadingFileId === row.id"
                                @click="emit('download', row.id)"
                                >下载</el-button
                            >
                            <el-button
                                link
                                type="danger"
                                :disabled="!canDeleteFile || unbindingFileId === row.id"
                                :loading="unbindingFileId === row.id"
                                @click="emit('unbind', row.id)"
                                >解绑</el-button
                            >
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>
            <el-tab-pane label="审计记录" name="audits">
                <el-table :data="audits" row-key="id">
                    <el-table-column label="操作" prop="operationType" width="120" />
                    <el-table-column label="原因" prop="operationReason" />
                    <el-table-column label="操作人" prop="operatedBy" width="120" />
                    <el-table-column label="时间" prop="operatedAt" width="180" />
                </el-table>
            </el-tab-pane>
        </el-tabs>
    </el-drawer>
</template>

<style scoped>
.file-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
}
.file-toolbar .el-input {
    width: 180px;
}
</style>
