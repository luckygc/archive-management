import { ref } from "vue";
import { defineStore } from "pinia";

import {
    errorMessage,
    getCurrentUser,
    HttpClientError,
    login,
    logout,
} from "@archive-management/frontend-core/api";
import type { CurrentUserDto, LoginRequest } from "@archive-management/frontend-core/types";

export const useSessionStore = defineStore("session", () => {
    const initialized = ref(false);
    const currentUser = ref<CurrentUserDto | null>(null);
    const initializationError = ref("");

    async function fetchCurrentUser() {
        try {
            initializationError.value = "";
            currentUser.value = await getCurrentUser();
        } catch (error) {
            currentUser.value = null;
            if (!(error instanceof HttpClientError) || error.status !== 401) {
                initializationError.value = errorMessage(error, "会话校验失败");
                throw error;
            }
        } finally {
            initialized.value = true;
        }
    }

    async function loginWithPassword(request: LoginRequest) {
        const session = await login(request);
        currentUser.value = {
            sessionId: session.sessionId,
            username: session.username,
            displayName: session.displayName,
            roles: session.roles,
        };
        initialized.value = true;
        return currentUser.value;
    }

    async function logoutCurrentUser() {
        const sessionId = currentUser.value?.sessionId;
        if (sessionId) {
            await logout(sessionId);
        }
        clearSession();
    }

    function clearSession() {
        currentUser.value = null;
        initialized.value = true;
        initializationError.value = "";
    }

    function reset() {
        currentUser.value = null;
        initialized.value = false;
        initializationError.value = "";
    }

    return {
        initialized,
        currentUser,
        initializationError,
        fetchCurrentUser,
        loginWithPassword,
        logoutCurrentUser,
        clearSession,
        reset,
    };
});
