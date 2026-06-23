<script setup lang="ts">
import { Lock, User } from "@element-plus/icons-vue";
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { CapErrorEvent, CapWidget } from "@cap.js/widget";
import { useSessionStore } from "../../app/stores/session";

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

const form = reactive({
  username: "",
  password: "",
});
const submitting = ref(false);
const errorMessage = ref("");
const powToken = ref("");
const securityMessage = ref("请完成安全验证");
const capWidget = ref<CapWidget | null>(null);

interface CapSolveEvent extends CustomEvent {
  detail: {
    token: string;
  };
}

async function submitLogin() {
  if (!form.username.trim() || !form.password) {
    errorMessage.value = "请输入账号和密码";
    return;
  }

  if (!powToken.value) {
    errorMessage.value = "请先完成安全验证";
    return;
  }

  submitting.value = true;
  errorMessage.value = "";
  try {
    securityMessage.value = "正在登录";
    await sessionStore.loginWithPassword({
      username: form.username.trim(),
      password: form.password,
      powToken: powToken.value,
    });
    const redirect = normalizeRedirect(route.query.redirect);
    await router.push(redirect);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "登录失败";
    resetCapWidget();
    securityMessage.value = "请重新完成安全验证";
  } finally {
    submitting.value = false;
  }
}

function resetCapWidget() {
  powToken.value = "";
  capWidget.value?.reset();
}

function handleCapSolve(event: Event) {
  powToken.value = (event as CapSolveEvent).detail.token;
  securityMessage.value = "安全验证已完成";
  errorMessage.value = "";
}

function handleCapReset() {
  powToken.value = "";
  securityMessage.value = "请完成安全验证";
}

function handleCapError(event: Event) {
  if (powToken.value) {
    return;
  }
  const detail = (event as CapErrorEvent).detail;
  powToken.value = "";
  securityMessage.value = detail?.message
    ? `安全验证失败：${detail.message}`
    : "安全验证失败，请重试";
}

function normalizeRedirect(value: unknown) {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) {
    return "/";
  }
  return value;
}
</script>

<template>
  <main class="login-page">
    <section class="login-page__intro" aria-label="系统信息">
      <div class="login-page__mark">AM</div>
      <div>
        <p class="login-page__eyebrow"></p>
        <h1>档案管理系统</h1>
        <p class="login-page__summary">档案入库、移交接收、借阅利用</p>
      </div>
    </section>

    <section class="login-page__panel" aria-label="登录">
      <div class="login-page__brand">
        <div>
          <h2>账号登录</h2>
          <p>进入档案业务工作台</p>
        </div>
      </div>
      <el-form class="login-page__form" @submit.prevent="submitLogin">
        <el-form-item>
          <el-input v-model="form.username" size="large" placeholder="账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            size="large"
            placeholder="密码"
            type="password"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="submitLogin"
          />
        </el-form-item>
        <div class="login-page__pow">
          <cap-widget
            ref="capWidget"
            data-cap-api-endpoint="/api/v1/auth/cap/"
            data-cap-worker-count="2"
            data-cap-i18n-initial-state="点击完成安全验证"
            data-cap-i18n-verifying-label="正在验证..."
            data-cap-i18n-solved-label="安全验证已完成"
            data-cap-i18n-error-label="验证失败"
            data-cap-i18n-verify-aria-label="完成安全验证"
            data-cap-i18n-verifying-aria-label="正在完成安全验证"
            data-cap-i18n-verified-aria-label="安全验证已完成"
            data-cap-i18n-error-aria-label="安全验证失败"
            @solve="handleCapSolve"
            @reset="handleCapReset"
            @error="handleCapError"
          />
          <p>{{ securityMessage }}</p>
        </div>
        <p v-if="errorMessage" class="login-page__error">{{ errorMessage }}</p>
        <el-button
          class="login-page__submit"
          type="primary"
          size="large"
          :loading="submitting"
          @click="submitLogin"
        >
          登录系统
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped lang="scss">
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 420px);
  gap: 48px;
  align-items: center;
  width: 100%;
  min-height: 100%;
  padding: 56px;
  background:
    radial-gradient(circle at 18% 22%, rgba(64, 158, 255, 0.16), transparent 28%),
    linear-gradient(135deg, #eef4fb 0%, #f7f9fc 48%, #e9f0f7 100%);
}

.login-page__intro {
  display: grid;
  max-width: 620px;
  gap: 22px;
}

.login-page__mark {
  display: grid;
  width: 64px;
  height: 64px;
  place-items: center;
  border-radius: 8px;
  color: #fff;
  background: var(--el-color-primary);
  font-size: 20px;
  font-weight: 700;
}

.login-page__eyebrow {
  margin: 0 0 12px;
  color: var(--el-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.login-page__intro h1 {
  margin: 0;
  color: var(--am-text);
  font-size: 40px;
  line-height: 1.2;
}

.login-page__summary {
  max-width: 32em;
  margin: 16px 0 0;
  color: var(--am-text-muted);
  font-size: 16px;
  line-height: 1.75;
}

.login-page__panel {
  width: 100%;
  border: 1px solid var(--am-border);
  border-radius: 8px;
  padding: 32px;
  background: var(--am-bg-surface);
}

.login-page__brand {
  display: flex;
  align-items: center;
  margin-bottom: 24px;

  h2 {
    margin: 0;
    font-size: 24px;
    line-height: 1.25;
  }

  p {
    margin: 4px 0 0;
    color: var(--am-text-muted);
  }
}

.login-page__form {
  display: grid;
  gap: 4px;
}

.login-page__error {
  min-height: 22px;
  margin: 0;
  color: var(--el-color-danger);
  font-size: 13px;
}

.login-page__submit {
  width: 100%;
  margin-top: 6px;
}

.login-page__pow {
  display: grid;
  gap: 6px;

  cap-widget {
    max-width: 100%;
  }

  p {
    min-height: 20px;
    margin: 0;
    color: var(--am-text-muted);
    font-size: 13px;
  }
}

@media (max-width: 860px) {
  .login-page {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 32px 20px;
  }

  .login-page__intro {
    max-width: none;
  }

  .login-page__intro h1 {
    font-size: 32px;
  }
}
</style>
