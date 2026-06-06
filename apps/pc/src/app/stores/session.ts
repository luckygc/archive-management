import { defineStore } from "pinia";
import { ref } from "vue";
import { getCurrentUser, login, logout } from "../../shared/api/auth";
import type { CurrentUserDto, LoginCommand } from "../../shared/types/auth";

export const useSessionStore = defineStore("session", () => {
  const initialized = ref(false);
  const currentUser = ref<CurrentUserDto | null>(null);

  async function fetchCurrentUser() {
    try {
      currentUser.value = await getCurrentUser();
    } catch {
      currentUser.value = null;
    } finally {
      initialized.value = true;
    }
  }

  async function loginWithPassword(command: LoginCommand) {
    currentUser.value = await login(command);
    initialized.value = true;
  }

  async function logoutCurrentUser() {
    await logout();
    currentUser.value = null;
    initialized.value = true;
  }

  return {
    initialized,
    currentUser,
    fetchCurrentUser,
    loginWithPassword,
    logoutCurrentUser,
  };
});
