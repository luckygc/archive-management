<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import type { CapWidget } from "cap-widget";

import { errorMessage, HttpClientError } from "@archive-management/frontend-core/api";
import {
    capWidgetApiEndpoint,
    createCapVerificationController,
} from "@archive-management/frontend-core/cap";
import type { TotpLoginChallengeDto } from "@archive-management/frontend-core/types";

import { usePageTabsStore } from "@/stores/pageTabsStore";
import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";

const props = withDefaults(defineProps<{ redirect?: string }>(), { redirect: "/" });
const router = useRouter();
const sessionStore = useSessionStore();
const permissionStore = usePermissionStore();
const pageTabsStore = usePageTabsStore();
const form = reactive({ username: "", password: "" });
const step = ref<"credentials" | "totp">("credentials");
const capWidget = ref<CapWidget | null>(null);
const powToken = ref("");
const securityMessage = ref("请完成安全验证");
const totpCode = ref("");
const totpChallenge = ref<TotpLoginChallengeDto | null>(null);
const submitting = ref(false);
const loginError = ref("");
let challengeExpiryTimer: number | undefined;
const controller = createCapVerificationController(() => capWidget.value);
const unsubscribe = controller.subscribe((state) => {
    powToken.value = state.powToken;
    securityMessage.value = state.securityMessage;
});

async function submitLogin() {
    if (submitting.value) return;
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
        const challenge = await sessionStore.loginWithPassword({
            username,
            password: form.password,
            powToken: powToken.value,
        });
        if (challenge) {
            enterTotpStep(challenge);
            return;
        }
        await completeLogin();
    } catch (error) {
        loginError.value = errorMessage(error, "登录失败");
        controller.reset("请重新完成安全验证");
    } finally {
        submitting.value = false;
    }
}

async function submitTotp() {
    if (submitting.value) return;
    const challenge = totpChallenge.value;
    if (!challenge || challengeExpired(challenge)) {
        returnToCredentials("二次验证已过期，请重新登录");
        return;
    }
    if (!/^\d{6}$/.test(totpCode.value)) {
        loginError.value = "请输入 6 位验证码";
        return;
    }

    submitting.value = true;
    loginError.value = "";
    try {
        await sessionStore.loginWithTotp(challenge.challengeToken, totpCode.value);
        clearChallenge();
        await completeLogin();
    } catch (error) {
        totpCode.value = "";
        if (error instanceof HttpClientError && error.code === "TOTP_CHALLENGE_INVALID") {
            returnToCredentials("二次验证已失效，请重新登录");
        } else {
            loginError.value = errorMessage(error, "验证码校验失败，请重试");
        }
    } finally {
        submitting.value = false;
    }
}

async function completeLogin() {
    pageTabsStore.reset();
    await permissionStore.fetchSummary().catch(() => undefined);
    await router.replace(props.redirect);
}

function enterTotpStep(challenge: TotpLoginChallengeDto) {
    clearChallengeExpiryTimer();
    form.password = "";
    powToken.value = "";
    totpCode.value = "";
    totpChallenge.value = challenge;
    loginError.value = "";
    step.value = "totp";
    controller.reset("请重新完成安全验证");

    const remainingMs = Date.parse(challenge.expiresAt) - Date.now();
    if (!Number.isFinite(remainingMs) || remainingMs <= 0) {
        returnToCredentials("二次验证已过期，请重新登录");
        return;
    }
    challengeExpiryTimer = window.setTimeout(
        () => returnToCredentials("二次验证已过期，请重新登录"),
        Math.min(remainingMs, 2_147_483_647),
    );
}

function returnToCredentials(message = "") {
    clearChallenge();
    form.password = "";
    step.value = "credentials";
    loginError.value = message;
    controller.reset("请重新完成安全验证");
}

function clearChallenge() {
    clearChallengeExpiryTimer();
    totpChallenge.value = null;
    totpCode.value = "";
}

function clearChallengeExpiryTimer() {
    if (challengeExpiryTimer === undefined) return;
    window.clearTimeout(challengeExpiryTimer);
    challengeExpiryTimer = undefined;
}

function challengeExpired(challenge: TotpLoginChallengeDto) {
    const expiresAt = Date.parse(challenge.expiresAt);
    return !Number.isFinite(expiresAt) || expiresAt <= Date.now();
}

function normalizeTotpCode(value: string) {
    totpCode.value = value.replace(/\D/g, "").slice(0, 6);
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
    form.password = "";
    powToken.value = "";
    clearChallenge();
    unsubscribe();
});
</script>

<template>
    <main class="am-login">
        <ElCard class="am-login__panel" shadow="never">
            <h1>{{ step === "credentials" ? "账号登录" : "二次验证" }}</h1>
            <p class="am-text-secondary">
                {{
                    step === "credentials"
                        ? "进入档案业务工作台"
                        : "请输入身份验证器生成的 6 位验证码"
                }}
            </p>
            <ElForm
                v-show="step === 'credentials'"
                label-position="top"
                @submit.prevent="submitLogin"
            >
                <ElFormItem label="账号"
                    ><ElInput v-model="form.username" autocomplete="username"
                /></ElFormItem>
                <ElFormItem label="密码"
                    ><ElInput
                        v-model="form.password"
                        autocomplete="current-password"
                        show-password
                        type="password"
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
            <ElForm v-if="step === 'totp'" label-position="top" @submit.prevent="submitTotp">
                <ElFormItem label="验证码">
                    <ElInput
                        :model-value="totpCode"
                        autocomplete="one-time-code"
                        autofocus
                        inputmode="numeric"
                        maxlength="6"
                        placeholder="6 位数字验证码"
                        @update:model-value="normalizeTotpCode"
                    />
                </ElFormItem>
                <p v-if="loginError" class="am-form-error" role="alert">{{ loginError }}</p>
                <ElButton
                    :loading="submitting"
                    native-type="submit"
                    type="primary"
                    class="am-login__submit"
                    >验证并登录</ElButton
                >
                <ElButton
                    :disabled="submitting"
                    class="am-login__secondary-action"
                    text
                    @click="returnToCredentials()"
                    >返回账号登录</ElButton
                >
            </ElForm>
        </ElCard>
    </main>
</template>
