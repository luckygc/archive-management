import { ElMessage, ElMessageBox } from "element-plus";
import { computed, ref } from "vue";
import {
    acceptArchiveIntakePackage,
    downloadArchiveIntakePackage,
    getArchiveIntakePackage,
    rejectArchiveIntakePackage,
} from "@/shared/api/intake";
import { requestErrorMessage } from "@/shared/requestError";
import type { ArchiveIntakePackageDetailResponse } from "@/shared/types/intake";

export function useIntakePackageReview(refreshHistory: () => Promise<void>) {
    const detailLoading = ref(false);
    const detailError = ref<string>();
    const detail = ref<ArchiveIntakePackageDetailResponse>();
    const detailId = ref<number>();
    const reviewing = ref(false);
    const downloading = ref(false);
    const review = ref(emptyReview());

    async function showDetail(id: number) {
        detailId.value = id;
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
            await refreshHistory();
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
            await refreshHistory();
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

    return {
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
    };
}
