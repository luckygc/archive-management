<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from "vue";
import { ElMessage } from "element-plus";

import {
    createTotpCredential,
    createTotpEnrollment,
    disableTotpCredential,
    errorMessage,
    HttpClientError,
} from "@archive-management/frontend-core/api";
import type { TotpEnrollmentDto } from "@archive-management/frontend-core/types";

import { totpQrCodeDataUrl } from "@/shared/security/totpQrCode";
import { useSessionStore } from "@/stores/sessionStore";

const sessionStore = useSessionStore();
const totpEnabled = computed(() => sessionStore.currentUser?.totpEnabled === true);
const enrollment = ref<TotpEnrollmentDto | null>(null);
const qrCodeUrl = ref("");
const preparing = ref(false);
const enabling = ref(false);
const disabling = ref(false);
const disableFormVisible = ref(false);
const actionError = ref("");
const enableForm = reactive({ currentPassword: "", code: "" });
const disableForm = reactive({ currentPassword: "", code: "" });
let enrollmentExpiryTimer: number | undefined;
let enrollmentRequestSequence = 0;
let componentActive = true;

async function startEnrollment() {
    if (preparing.value) return;
    preparing.value = true;
    actionError.value = "";
    clearEnrollment();
    const requestSequence = enrollmentRequestSequence;
    try {
        const prepared = await createTotpEnrollment();
        if (!componentActive || requestSequence !== enrollmentRequestSequence) return;
        const remainingMs = Date.parse(prepared.expiresAt) - Date.now();
        if (!Number.isFinite(remainingMs) || remainingMs <= 0) {
            actionError.value = "设置已过期，请重新开始";
            return;
        }
        enrollment.value = prepared;
        qrCodeUrl.value = totpQrCodeDataUrl(prepared.otpauthUri);
        enrollmentExpiryTimer = window.setTimeout(
            expireEnrollment,
            Math.min(remainingMs, 2_147_483_647),
        );
    } catch (error) {
        actionError.value = errorMessage(error, "无法开始设置身份验证器");
    } finally {
        preparing.value = false;
    }
}

async function confirmEnrollment() {
    if (enabling.value) return;
    const prepared = enrollment.value;
    if (!prepared) return;
    if (!enableForm.currentPassword) {
        actionError.value = "请输入当前密码";
        return;
    }
    if (!/^\d{6}$/.test(enableForm.code)) {
        actionError.value = "请输入身份验证器生成的 6 位验证码";
        return;
    }

    enabling.value = true;
    actionError.value = "";
    try {
        const credentialStatus = await createTotpCredential({
            enrollmentToken: prepared.enrollmentToken,
            currentPassword: enableForm.currentPassword,
            code: enableForm.code,
        });
        clearEnrollment();
        await refreshTotpState(credentialStatus.totpEnabled, "身份验证器已启用");
    } catch (error) {
        if (error instanceof HttpClientError && error.code === "TOTP_ENROLLMENT_INVALID") {
            clearEnrollment();
            actionError.value = "设置已过期，请重新开始";
        } else if (error instanceof HttpClientError && error.code === "TOTP_ALREADY_ENABLED") {
            clearEnrollment();
            await refreshTotpState(true, "身份验证器已经启用");
            actionError.value = "身份验证器已经启用";
        } else {
            actionError.value = errorMessage(error, "启用失败，请检查密码和验证码");
        }
    } finally {
        enableForm.currentPassword = "";
        enableForm.code = "";
        enabling.value = false;
    }
}

function openDisableForm() {
    clearEnrollment();
    disableFormVisible.value = true;
    actionError.value = "";
}

async function confirmDisable() {
    if (disabling.value) return;
    if (!disableForm.currentPassword) {
        actionError.value = "请输入当前密码";
        return;
    }
    if (!/^\d{6}$/.test(disableForm.code)) {
        actionError.value = "请输入身份验证器生成的 6 位验证码";
        return;
    }

    disabling.value = true;
    actionError.value = "";
    try {
        await disableTotpCredential({
            currentPassword: disableForm.currentPassword,
            code: disableForm.code,
        });
        closeDisableForm();
        await refreshTotpState(false, "身份验证器已停用");
    } catch (error) {
        actionError.value = errorMessage(error, "停用失败，请检查密码和验证码");
    } finally {
        disableForm.currentPassword = "";
        disableForm.code = "";
        disabling.value = false;
    }
}

async function refreshTotpState(totpEnabled: boolean, successMessage: string) {
    sessionStore.setTotpEnabled(totpEnabled);
    try {
        await sessionStore.fetchCurrentUser();
        ElMessage.success(successMessage);
    } catch {
        ElMessage.warning(`${successMessage}，但状态刷新失败，请稍后重新打开页面确认`);
    }
}

async function copyManualKey() {
    const manualKey = enrollment.value?.manualKey;
    if (!manualKey) return;
    try {
        await navigator.clipboard.writeText(manualKey);
        ElMessage.success("手工密钥已复制");
    } catch {
        actionError.value = "无法自动复制，请手动选择密钥";
    }
}

function normalizeEnableCode(value: string) {
    enableForm.code = normalizeCode(value);
}

function normalizeDisableCode(value: string) {
    disableForm.code = normalizeCode(value);
}

function normalizeCode(value: string) {
    return value.replace(/\D/g, "").slice(0, 6);
}

function expireEnrollment() {
    clearEnrollment();
    actionError.value = "设置已过期，请重新开始";
}

function clearEnrollment() {
    enrollmentRequestSequence += 1;
    if (enrollmentExpiryTimer !== undefined) {
        window.clearTimeout(enrollmentExpiryTimer);
        enrollmentExpiryTimer = undefined;
    }
    enrollment.value = null;
    qrCodeUrl.value = "";
    enableForm.currentPassword = "";
    enableForm.code = "";
}

function cancelEnrollment() {
    clearEnrollment();
    actionError.value = "";
}

function closeDisableForm() {
    disableFormVisible.value = false;
    disableForm.currentPassword = "";
    disableForm.code = "";
    actionError.value = "";
}

onBeforeUnmount(() => {
    componentActive = false;
    clearEnrollment();
    closeDisableForm();
});
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>账号安全</h1>
        </div>

        <ElCard class="account-security" shadow="never">
            <div class="account-security__status">
                <div>
                    <h2>身份验证器</h2>
                    <p class="am-text-secondary">
                        启用后，账号密码与安全验证通过后还需输入动态验证码。
                    </p>
                </div>
                <ElTag :type="totpEnabled ? 'success' : 'info'">
                    {{ totpEnabled ? "已启用" : "未启用" }}
                </ElTag>
            </div>

            <template v-if="!totpEnabled">
                <ElButton
                    v-if="!enrollment"
                    type="primary"
                    :loading="preparing"
                    @click="startEnrollment"
                >
                    启用身份验证器
                </ElButton>

                <div v-else class="account-security__enrollment">
                    <div class="account-security__qr">
                        <img :src="qrCodeUrl" alt="身份验证器配置二维码" />
                        <span class="am-text-secondary">使用身份验证器扫描二维码</span>
                    </div>
                    <div class="account-security__setup">
                        <ElAlert
                            title="密钥只在本次设置中展示。确认前请先保存到身份验证器。"
                            type="warning"
                            :closable="false"
                            show-icon
                        />
                        <ElForm label-position="top" @submit.prevent="confirmEnrollment">
                            <ElFormItem label="手工密钥">
                                <ElInput
                                    :model-value="enrollment.manualKey"
                                    aria-label="手工密钥"
                                    readonly
                                >
                                    <template #append>
                                        <ElButton @click="copyManualKey">复制</ElButton>
                                    </template>
                                </ElInput>
                            </ElFormItem>
                            <ElFormItem label="当前密码">
                                <ElInput
                                    v-model="enableForm.currentPassword"
                                    autocomplete="current-password"
                                    :disabled="enabling"
                                    show-password
                                    type="password"
                                />
                            </ElFormItem>
                            <ElFormItem label="验证码">
                                <ElInput
                                    :model-value="enableForm.code"
                                    autocomplete="one-time-code"
                                    :disabled="enabling"
                                    inputmode="numeric"
                                    maxlength="6"
                                    placeholder="6 位数字验证码"
                                    @update:model-value="normalizeEnableCode"
                                />
                            </ElFormItem>
                            <div class="account-security__actions">
                                <ElButton :disabled="enabling" @click="cancelEnrollment"
                                    >取消</ElButton
                                >
                                <ElButton native-type="submit" type="primary" :loading="enabling">
                                    确认启用
                                </ElButton>
                            </div>
                        </ElForm>
                    </div>
                </div>
            </template>

            <template v-else>
                <ElButton v-if="!disableFormVisible" type="danger" plain @click="openDisableForm">
                    停用身份验证器
                </ElButton>
                <div v-else class="account-security__disable">
                    <ElAlert
                        title="停用后，后续登录将不再要求动态验证码。"
                        type="warning"
                        :closable="false"
                        show-icon
                    />
                    <ElForm label-position="top" @submit.prevent="confirmDisable">
                        <ElFormItem label="当前密码">
                            <ElInput
                                v-model="disableForm.currentPassword"
                                autocomplete="current-password"
                                :disabled="disabling"
                                show-password
                                type="password"
                            />
                        </ElFormItem>
                        <ElFormItem label="验证码">
                            <ElInput
                                :model-value="disableForm.code"
                                autocomplete="one-time-code"
                                :disabled="disabling"
                                inputmode="numeric"
                                maxlength="6"
                                placeholder="6 位数字验证码"
                                @update:model-value="normalizeDisableCode"
                            />
                        </ElFormItem>
                        <div class="account-security__actions">
                            <ElButton :disabled="disabling" @click="closeDisableForm"
                                >取消</ElButton
                            >
                            <ElButton native-type="submit" type="danger" :loading="disabling">
                                确认停用
                            </ElButton>
                        </div>
                    </ElForm>
                </div>
            </template>

            <p v-if="actionError" class="am-form-error account-security__error" role="alert">
                {{ actionError }}
            </p>
        </ElCard>
    </section>
</template>

<style scoped>
.account-security {
    width: min(760px, 100%);
}

.account-security__status {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 20px;
}

.account-security__status h2 {
    margin: 0 0 6px;
    font-size: 18px;
}

.account-security__status p {
    margin: 0;
}

.account-security__enrollment {
    display: flex;
    align-items: flex-start;
    gap: 24px;
    padding-top: 4px;
}

.account-security__qr {
    display: grid;
    flex: 0 0 216px;
    justify-items: center;
    gap: 8px;
}

.account-security__qr img {
    display: block;
    width: 216px;
    height: 216px;
    border: 1px solid #e6eaf2;
    border-radius: 8px;
}

.account-security__setup,
.account-security__disable {
    display: grid;
    flex: 1;
    gap: 16px;
    min-width: 0;
}

.account-security__disable {
    max-width: 520px;
}

.account-security__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
}

.account-security__error {
    margin-top: 16px;
    margin-bottom: 0;
}

@media (max-width: 720px) {
    .account-security__enrollment {
        flex-direction: column;
    }

    .account-security__qr {
        align-self: center;
    }

    .account-security__setup {
        width: 100%;
    }
}
</style>
