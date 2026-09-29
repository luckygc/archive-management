<script setup lang="ts">
import { computed, ref } from "vue";
import { totpQrCodeDataUrl } from "@/shared/security/totpQrCode";

const props = defineProps<{ manualKey: string; otpauthUri: string }>();
const qrCodeUrl = computed(() => totpQrCodeDataUrl(props.otpauthUri));
const copyFailed = ref(false);

async function copyKey() {
    try {
        await navigator.clipboard.writeText(props.manualKey);
        copyFailed.value = false;
    } catch {
        copyFailed.value = true;
    }
}
</script>

<template>
    <div class="am-login__enrollment">
        <ElAlert
            title="先将密钥保存到身份验证器，再输入其中生成的验证码。离开此页后密钥不会再次显示。"
            type="warning"
            :closable="false"
            show-icon
        />
        <img :src="qrCodeUrl" alt="TOTP 身份验证器配置二维码" />
        <ElInput :model-value="manualKey" aria-label="TOTP 手工密钥" readonly>
            <template #append><ElButton @click="copyKey">复制密钥</ElButton></template>
        </ElInput>
        <p v-if="copyFailed" role="alert">复制失败，请手动选择密钥保存。</p>
    </div>
</template>

<style scoped>
.am-login__enrollment {
    display: grid;
    gap: 12px;
    margin-bottom: 16px;
}

.am-login__enrollment img {
    width: min(100%, 220px);
    height: auto;
    justify-self: center;
}
</style>
