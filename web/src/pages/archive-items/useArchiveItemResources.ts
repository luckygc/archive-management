import { ElMessage } from "element-plus";
import { computed, reactive, ref } from "vue";

import { errorMessage } from "@archive-management/frontend-core/api";

import {
    downloadArchiveItemElectronicFile,
    listArchiveItemAudits,
    listArchiveItemElectronicFiles,
    unbindArchiveItemElectronicFile,
    uploadArchiveItemElectronicFile,
} from "@/shared/api/archive-records";
import type {
    ArchiveItemAuditDto,
    ArchiveItemElectronicFileDto,
} from "@/shared/types/archive-records";
import { usePermissionStore } from "@/stores/permissionStore";

export function useArchiveItemResources(openLink: (href: string) => void) {
    const permissionStore = usePermissionStore();
    const canCreateElectronicFile = computed(
        () =>
            permissionStore.has("archive:item:create") ||
            permissionStore.has("archive:item:update"),
    );
    const canDeleteElectronicFile = computed(() => permissionStore.has("archive:item:delete"));
    const canDownloadFile = computed(() =>
        permissionStore.has("archive:item:download-electronic-file"),
    );
    const canReadAudit = computed(
        () =>
            permissionStore.superAdmin ||
            permissionStore.has("archive:item:audit:read") ||
            permissionStore.has("archive:item:read-audit"),
    );
    const drawerState = ref<{
        archiveItemId: number;
        activeKey: "files" | "audits" | "relations";
    }>();
    const files = ref<ArchiveItemElectronicFileDto[]>([]);
    const audits = ref<ArchiveItemAuditDto[]>([]);
    const fileForm = reactive({ usageType: "", displayOrder: undefined as number | undefined });
    const drawerLoading = ref(false);
    const uploading = ref(false);
    const downloadingFileId = ref<number>();
    const unbindingFileId = ref<number>();

    async function openDrawer(value: unknown, activeKey: "files" | "audits" | "relations") {
        const id = rowId(value);
        if (!id) return;
        drawerState.value = { archiveItemId: id, activeKey };
        await loadDrawer();
    }

    async function loadDrawer() {
        if (!drawerState.value) return;
        const state = { ...drawerState.value };
        drawerLoading.value = true;
        try {
            if (state.activeKey === "files") {
                const response = await listArchiveItemElectronicFiles(state.archiveItemId);
                if (drawerState.value?.archiveItemId === state.archiveItemId)
                    files.value = response.items;
            } else if (state.activeKey === "audits") {
                const response = await listArchiveItemAudits({
                    archiveItemId: state.archiveItemId,
                    limit: 20,
                    requestTotal: true,
                });
                if (drawerState.value?.archiveItemId === state.archiveItemId)
                    audits.value = response.items;
            }
        } catch (error) {
            ElMessage.error(errorMessage(error, "加载档案关联信息失败"));
        } finally {
            drawerLoading.value = false;
        }
    }

    async function changeDrawerTab(value: string | number) {
        if (!drawerState.value) return;
        drawerState.value.activeKey = String(value) as "files" | "audits" | "relations";
        await loadDrawer();
    }

    async function uploadElectronicFile(event: Event) {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file || !drawerState.value) return;
        uploading.value = true;
        try {
            await uploadArchiveItemElectronicFile(drawerState.value.archiveItemId, file, {
                usageType: fileForm.usageType || undefined,
                displayOrder: fileForm.displayOrder,
            });
            ElMessage.success("附件已上传");
            await loadDrawer();
        } catch (error) {
            ElMessage.error(errorMessage(error, "上传附件失败"));
        } finally {
            uploading.value = false;
            input.value = "";
        }
    }

    async function downloadFile(id: number) {
        if (!drawerState.value) return;
        downloadingFileId.value = id;
        try {
            openLink(
                (await downloadArchiveItemElectronicFile(drawerState.value.archiveItemId, id)).href,
            );
        } catch (error) {
            ElMessage.error(errorMessage(error, "下载附件失败"));
        } finally {
            downloadingFileId.value = undefined;
        }
    }

    async function unbindFile(id: number) {
        if (!drawerState.value) return;
        unbindingFileId.value = id;
        try {
            await unbindArchiveItemElectronicFile(drawerState.value.archiveItemId, id);
            ElMessage.success("文件已解绑");
            await loadDrawer();
        } catch (error) {
            ElMessage.error(errorMessage(error, "解绑文件失败"));
        } finally {
            unbindingFileId.value = undefined;
        }
    }

    return {
        audits,
        canCreateElectronicFile,
        canDeleteElectronicFile,
        canDownloadFile,
        canReadAudit,
        changeDrawerTab,
        downloadingFileId,
        downloadFile,
        drawerLoading,
        drawerState,
        fileForm,
        files,
        openDrawer,
        unbindingFileId,
        unbindFile,
        uploading,
        uploadElectronicFile,
    };
}

function rowId(value: unknown) {
    const id = (value as Record<string, unknown>).id;
    return typeof id === "number" ? id : undefined;
}
