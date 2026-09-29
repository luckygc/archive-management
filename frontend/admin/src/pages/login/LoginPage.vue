<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import {
    errorMessage,
    HttpClientError,
    PROBLEM_TYPES,
} from "@archive-management/frontend-core/api";
import type { TotpLoginChallengeDto } from "@archive-management/frontend-core/types";

import { usePageTabsStore } from "@/stores/pageTabsStore";
import { usePermissionStore } from "@/stores/permissionStore";
import { useSessionStore } from "@/stores/sessionStore";
import TotpLoginEnrollment from "./TotpLoginEnrollment.vue";

const props = withDefaults(defineProps<{ redirect?: string }>(), { redirect: "/" });
const router = useRouter();
const sessionStore = useSessionStore();
const permissionStore = usePermissionStore();
const pageTabsStore = usePageTabsStore();
const form = reactive({ username: "", password: "" });
const step = ref<"credentials" | "totp">("credentials");
const totpCode = ref("");
const totpChallenge = ref<TotpLoginChallengeDto | null>(null);
const enrollment = computed(() => {
    const challenge = totpChallenge.value;
    return challenge?.manualKey && challenge.otpauthUri
        ? { manualKey: challenge.manualKey, otpauthUri: challenge.otpauthUri }
        : null;
});
const submitting = ref(false);
const loginError = ref("");
let challengeExpiryTimer: number | undefined;

async function submitLogin() {
    if (submitting.value) return;
    const username = form.username.trim();
    if (!username || !form.password) {
        loginError.value = "请输入账号和密码";
        return;
    }

    submitting.value = true;
    loginError.value = "";
    try {
        const challenge = await sessionStore.loginWithPassword({
            username,
            password: form.password,
        });
        if (challenge) {
            enterTotpStep(challenge);
            return;
        }
        await completeLogin();
    } catch (error) {
        loginError.value = errorMessage(error, "登录失败");
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
        if (
            error instanceof HttpClientError &&
            error.type === PROBLEM_TYPES.TOTP_CHALLENGE_INVALID
        ) {
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
    totpCode.value = "";
    totpChallenge.value = challenge;
    loginError.value = "";
    step.value = "totp";

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

function isTotpDigit(value: string) {
    return /^\d$/.test(value);
}

onMounted(() => {
    if (sessionStore.currentUser) {
        void router.replace(props.redirect);
        return;
    }
});

onBeforeUnmount(() => {
    form.password = "";
    clearChallenge();
});
</script>

<template>
    <main class="am-login">
        <ElCard class="am-login__panel" shadow="never">
            <h1>
                {{
                    step === "credentials" ? "账号登录" : enrollment ? "设置身份验证器" : "二次验证"
                }}
            </h1>
            <p class="am-text-secondary">
                {{
                    step === "credentials"
                        ? "进入档案业务工作台"
                        : enrollment
                          ? "请保存密钥并完成首次验证"
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
                <TotpLoginEnrollment
                    v-if="enrollment"
                    :manual-key="enrollment.manualKey"
                    :otpauth-uri="enrollment.otpauthUri"
                />
                <ElFormItem label="验证码">
                    <ElInputOtp v-model="totpCode" :validator="isTotpDigit" inputmode="numeric" />
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
