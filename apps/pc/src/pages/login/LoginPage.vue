<script setup lang="ts">
import { Lock, User } from "@element-plus/icons-vue";
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useSessionStore } from "../../app/stores/session";

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

const form = reactive({
  username: "admin",
  password: "admin",
});
const submitting = ref(false);
const errorMessage = ref("");

async function submitLogin() {
  if (!form.username.trim() || !form.password) {
    errorMessage.value = "请输入账号和密码";
    return;
  }

  submitting.value = true;
  errorMessage.value = "";
  try {
    await sessionStore.loginWithPassword({
      username: form.username.trim(),
      password: form.password,
    });
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    await router.push(redirect);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "登录失败";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-page__panel">
      <div class="login-page__brand">
        <span>AM</span>
        <div>
          <h1>档案管理</h1>
          <p>统一档案业务入口</p>
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
        <p v-if="errorMessage" class="login-page__error">{{ errorMessage }}</p>
        <el-button
          class="login-page__submit"
          type="primary"
          size="large"
          :loading="submitting"
          @click="submitLogin"
        >
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped lang="scss">
.login-page {
  display: grid;
  width: 100%;
  min-height: 100%;
  place-items: center;
  padding: 40px;
  background:
    linear-gradient(135deg, rgba(17, 24, 39, 0.88), rgba(29, 78, 216, 0.74)),
    url("https://images.unsplash.com/photo-1484480974693-6ca0a78fb36b?auto=format&fit=crop&w=1800&q=80")
      center / cover;
}

.login-page__panel {
  width: min(420px, 100%);
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 8px;
  padding: 30px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 48px rgba(17, 24, 39, 0.24);
}

.login-page__brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;

  > span {
    display: grid;
    width: 44px;
    height: 44px;
    place-items: center;
    border-radius: 8px;
    color: #fff;
    background: #1d4ed8;
    font-weight: 700;
  }

  h1 {
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
</style>
