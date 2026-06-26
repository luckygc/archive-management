import type { Ref } from "vue";
import { ref } from "vue";
import type { CapErrorEvent, CapSolveEvent, CapWidget } from "cap-widget";

export function useCapVerification(capWidgetRef: Readonly<Ref<CapWidget | null>>) {
    const powToken = ref("");
    const securityMessage = ref("请完成安全验证");

    function resetCapWidget(message = "请完成安全验证") {
        powToken.value = "";
        capWidgetRef.value?.reset();
        securityMessage.value = message;
    }

    function handleCapSolve(event: Event) {
        powToken.value = (event as CapSolveEvent).detail.token;
        securityMessage.value = "安全验证已完成";
    }

    function handleCapReset() {
        powToken.value = "";
        securityMessage.value = "请完成安全验证";
    }

    function handleCapError(event: Event) {
        powToken.value = "";
        const detail = (event as CapErrorEvent).detail;
        securityMessage.value = detail?.message
            ? `安全验证失败：${detail.message}`
            : "安全验证失败，请重试";
    }

    return {
        powToken,
        securityMessage,
        resetCapWidget,
        handleCapSolve,
        handleCapReset,
        handleCapError,
    };
}
