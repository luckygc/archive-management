<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import type { CapWidget } from "cap-widget";

import { errorMessage } from "@archive-management/frontend-core/api";
import {
    capWidgetApiEndpoint,
    createCapVerificationController,
} from "@archive-management/frontend-core/cap";

import { usePageTabsStore } from "@/stores/pageTabsStore";
import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";

const props = withDefaults(defineProps<{ redirect?: string }>(), { redirect: "/" });
const router = useRouter();
const sessionStore = useSessionStore();
const permissionStore = usePermissionStore();
const pageTabsStore = usePageTabsStore();
const form = reactive({ username: "", password: "" });
const capWidget = ref<CapWidget | null>(null);
const powToken = ref("");
const securityMessage = ref("请完成安全验证");
const submitting = ref(false);
const loginError = ref("");
const controller = createCapVerificationController(() => capWidget.value);
const unsubscribe = controller.subscribe((state) => {
    powToken.value = state.powToken;
    securityMessage.value = state.securityMessage;
});

async function submitLogin() {
    const username = form.username.trim();
    if (!username || !form.password) {
        loginError.value = "请输入账号和密码";
        return;
    }
    if (!powToken.value) {
        loginError.value = "请先完成安全验证";
        return;
    }

    submitting.value = true;
    loginError.value = "";
    try {
        await sessionStore.loginWithPassword({
            username,
            password: form.password,
            powToken: powToken.value,
        });
        pageTabsStore.reset();
        await permissionStore.fetchSummary().catch(() => undefined);
        await router.replace(props.redirect);
    } catch (error) {
        loginError.value = errorMessage(error, "登录失败");
        controller.reset("请重新完成安全验证");
    } finally {
        submitting.value = false;
    }
}

onMounted(() => {
    if (sessionStore.currentUser) {
        void router.replace(props.redirect);
        return;
    }
    capWidget.value?.addEventListener("solve", controller.handleSolve);
    capWidget.value?.addEventListener("reset", controller.handleReset);
    capWidget.value?.addEventListener("error", controller.handleError);
});

onBeforeUnmount(() => {
    capWidget.value?.removeEventListener("solve", controller.handleSolve);
    capWidget.value?.removeEventListener("reset", controller.handleReset);
    capWidget.value?.removeEventListener("error", controller.handleError);
    unsubscribe();
});
</script>

<template>
    <main class="am-login">
        <ElCard class="am-login__panel" shadow="never">
            <h1>账号登录</h1>
            <p class="am-text-secondary">进入档案业务工作台</p>
            <ElForm label-position="top" @submit.prevent="submitLogin">
                <ElFormItem label="账号"
                    ><ElInput v-model="form.username" autocomplete="username"
                /></ElFormItem>
                <ElFormItem label="密码"
                    ><ElInput
                        v-model="form.password"
                        autocomplete="current-password"
                        show-password
                        type="password"
                        @keyup.enter="submitLogin"
                /></ElFormItem>
                <div class="am-login__pow">
                    <cap-widget
                        ref="capWidget"
                        :data-cap-api-endpoint="capWidgetApiEndpoint()"
                        data-cap-hidden-field-name="powToken"
                        data-cap-i18n-error-aria-label="安全验证失败"
                        data-cap-i18n-error-label="验证失败"
                        data-cap-i18n-initial-state="点击完成安全验证"
                        data-cap-i18n-solved-label="安全验证已完成"
                        data-cap-i18n-verified-aria-label="安全验证已完成"
                        data-cap-i18n-verifying-aria-label="正在完成安全验证"
                        data-cap-i18n-verifying-label="正在验证..."
                        data-cap-i18n-verify-aria-label="完成安全验证"
                        data-cap-worker-count="2"
                        data-testid="cap-widget"
                        required
                    />
                    <span :class="powToken ? 'am-text-success' : 'am-text-secondary'">{{
                        securityMessage
                    }}</span>
                </div>
                <p v-if="loginError" class="am-form-error" role="alert">{{ loginError }}</p>
                <ElButton
                    :loading="submitting"
                    native-type="submit"
                    type="primary"
                    class="am-login__submit"
                    >登录系统</ElButton
                >
            </ElForm>
        </ElCard>
    </main>
</template>
