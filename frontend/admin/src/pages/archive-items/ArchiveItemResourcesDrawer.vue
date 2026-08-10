<script setup lang="ts">
import { Upload } from "@element-plus/icons-vue";

import type {
    ArchiveItemAuditDto,
    ArchiveItemElectronicFileDto,
} from "@/shared/types/archive-records";
import RequestErrorState from "@/shared/components/RequestErrorState.vue";
import { AmDataTable } from "@/shared/components/data-table";
import ArchiveItemRelationsDrawer from "./ArchiveItemRelationsDrawer.vue";

defineProps<{
    state?: { archiveItemId: number; activeKey: "files" | "audits" | "relations" };
    loading: boolean;
    loadError?: string;
    fileForm: { usageType: string; displayOrder?: number };
    files: ArchiveItemElectronicFileDto[];
    audits: ArchiveItemAuditDto[];
    canCreateFile: boolean;
    canDeleteFile: boolean;
    canDownloadFile: boolean;
    canReadRelation: boolean;
    canUpdateRelation: boolean;
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
    retry: [];
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
        :title="state ? `档案 ${state.archiveItemId}` : ''"
        size="70%"
        @close="emit('close')"
    >
        <el-tabs :model-value="state?.activeKey" @tab-change="emit('tabChange', $event)">
            <el-tab-pane label="电子文件" name="files">
                <div v-loading="loading" class="resource-pane">
                    <RequestErrorState
                        v-if="loadError && state?.activeKey === 'files'"
                        :message="loadError"
                        :retrying="loading"
                        @retry="emit('retry')"
                    />
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
                        <input
                            ref="uploadInput"
                            hidden
                            type="file"
                            @change="emit('upload', $event)"
                        />
                    </div>
                    <AmDataTable
                        :data="files"
                        :loading="loading"
                        row-key="id"
                        :columns="[
                            {
                                key: 'originalFilename',
                                label: '文件名',
                                sortable: true,
                            },
                            { key: 'fileSize', label: '大小', width: 100, sortable: true },
                            { key: 'usageType', label: '用途', width: 100, sortable: true },
                            { key: 'actions', label: '操作', width: 140 },
                        ]"
                    >
                        <template #cell-fileSize="{ row }">{{ formatSize(row.fileSize) }}</template>
                        <template #cell-actions="{ row }">
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
                    </AmDataTable>
                </div>
            </el-tab-pane>
            <el-tab-pane label="审计记录" name="audits">
                <div v-loading="loading" class="resource-pane">
                    <RequestErrorState
                        v-if="loadError && state?.activeKey === 'audits'"
                        :message="loadError"
                        :retrying="loading"
                        @retry="emit('retry')"
                    />
                    <AmDataTable
                        :data="audits"
                        :loading="loading"
                        row-key="id"
                        :columns="[
                            { key: 'operationType', label: '操作', width: 120, sortable: true },
                            { key: 'operationReason', label: '原因', sortable: true },
                            { key: 'operatedBy', label: '操作人', width: 120, sortable: true },
                            { key: 'operatedAt', label: '时间', width: 180, sortable: true },
                        ]"
                    />
                </div>
            </el-tab-pane>
            <el-tab-pane v-if="canReadRelation" label="档案关系" name="relations">
                <ArchiveItemRelationsDrawer
                    v-if="state"
                    :archive-item-id="state.archiveItemId"
                    :active="state.activeKey === 'relations'"
                    :can-update="canUpdateRelation"
                />
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
.resource-pane {
    min-height: 120px;
}
</style>
