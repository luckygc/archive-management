import { ElMessage, ElMessageBox } from "element-plus";
import { ref } from "vue";

import { errorMessage } from "@archive-management/frontend-core/api";

import {
    deleteArchiveRecord,
    lockArchiveRecord,
    unlockArchiveRecord,
} from "@/shared/api/archive-records";

type ArchiveItemLifecycleAction = "lock" | "unlock" | "delete";

export function useArchiveItemLifecycle(refresh: () => void | Promise<void>) {
    const busyAction = ref<ArchiveItemLifecycleAction>();

    async function lock(id: number) {
        if (busyAction.value) return;
        busyAction.value = "lock";
        try {
            const { value } = await ElMessageBox.prompt("请输入锁定原因", "锁定档案", {
                confirmButtonText: "锁定",
                cancelButtonText: "取消",
                inputValidator: (reason) => Boolean(reason.trim()) || "请输入锁定原因",
                type: "warning",
            });
            const reason = value.trim();
            if (!reason) return;
            await lockArchiveRecord(id, reason);
            await refresh();
            ElMessage.success("档案已锁定");
        } catch (error) {
            if (!isDialogCancellation(error)) ElMessage.error(errorMessage(error, "锁定档案失败"));
        } finally {
            busyAction.value = undefined;
        }
    }

    async function unlock(id: number) {
        if (busyAction.value) return;
        busyAction.value = "unlock";
        try {
            await ElMessageBox.confirm("确认解锁此档案？", "解锁档案", {
                confirmButtonText: "解锁",
                cancelButtonText: "取消",
                type: "warning",
            });
            await unlockArchiveRecord(id);
            await refresh();
            ElMessage.success("档案已解锁");
        } catch (error) {
            if (!isDialogCancellation(error)) ElMessage.error(errorMessage(error, "解锁档案失败"));
        } finally {
            busyAction.value = undefined;
        }
    }

    async function remove(id: number) {
        if (busyAction.value) return;
        busyAction.value = "delete";
        try {
            await ElMessageBox.confirm("删除后档案将进入回收站，确认删除？", "删除档案", {
                confirmButtonText: "删除",
                cancelButtonText: "取消",
                confirmButtonClass: "el-button--danger",
                type: "warning",
            });
            await deleteArchiveRecord(id);
            await refresh();
            ElMessage.success("档案已删除");
        } catch (error) {
            if (!isDialogCancellation(error)) ElMessage.error(errorMessage(error, "删除档案失败"));
        } finally {
            busyAction.value = undefined;
        }
    }

    return { busyAction, lock, remove, unlock };
}

function isDialogCancellation(error: unknown) {
    return error === "cancel" || error === "close";
}
