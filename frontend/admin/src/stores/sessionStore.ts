import { ref } from "vue";
import { defineStore } from "pinia";

import {
    errorMessage,
    getCurrentUser,
    HttpClientError,
    login,
    logout,
    verifyTotpLoginChallenge,
} from "@archive-management/frontend-core/api";
import type {
    CurrentUserDto,
    LoginRequest,
    LoginSessionDto,
    TotpLoginChallengeDto,
} from "@archive-management/frontend-core/types";

export const useSessionStore = defineStore("session", () => {
    const initialized = ref(false);
    const currentUser = ref<CurrentUserDto | null>(null);
    const initializationError = ref("");

    async function fetchCurrentUser() {
        try {
            initializationError.value = "";
            currentUser.value = await getCurrentUser();
        } catch (error) {
            if (error instanceof HttpClientError && error.status === 401) {
                currentUser.value = null;
            } else {
                initializationError.value = errorMessage(error, "会话校验失败");
                throw error;
            }
        } finally {
            initialized.value = true;
        }
    }

    async function loginWithPassword(
        request: LoginRequest,
    ): Promise<TotpLoginChallengeDto | undefined> {
        const result = await login(request);
        if (result.status === 202) {
            return result.challenge;
        }
        setAuthenticatedSession(result.session, false);
        return undefined;
    }

    async function loginWithTotp(challengeToken: string, code: string) {
        const session = await verifyTotpLoginChallenge({ challengeToken, code });
        setAuthenticatedSession(session, true);
        return currentUser.value;
    }

    function setAuthenticatedSession(session: LoginSessionDto, totpEnabled: boolean) {
        currentUser.value = {
            sessionId: session.sessionId,
            username: session.username,
            displayName: session.displayName,
            roles: session.roles,
            totpEnabled,
        };
        initialized.value = true;
    }

    function setTotpEnabled(totpEnabled: boolean) {
        if (currentUser.value) currentUser.value = { ...currentUser.value, totpEnabled };
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
        loginWithTotp,
        setTotpEnabled,
        logoutCurrentUser,
        clearSession,
        reset,
    };
});
