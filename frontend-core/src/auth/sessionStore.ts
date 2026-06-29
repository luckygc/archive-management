import { create } from "zustand";

import { getCurrentUser, HttpClientError, login, logout } from "../api";
import type { CurrentUserDto, LoginCommand } from "../types";

interface SessionState {
    initialized: boolean;
    currentUser: CurrentUserDto | null;
    fetchCurrentUser: () => Promise<void>;
    loginWithPassword: (command: LoginCommand) => Promise<CurrentUserDto>;
    logoutCurrentUser: () => Promise<void>;
    clearSession: () => void;
    reset: () => void;
}

const initialState = {
    initialized: false,
    currentUser: null,
};

const sessionResetHandlers = new Set<() => void>();

export function registerSessionResetHandler(handler: () => void) {
    sessionResetHandlers.add(handler);
    return () => {
        sessionResetHandlers.delete(handler);
    };
}

export const useSessionStore = create<SessionState>((set, get) => ({
    ...initialState,
    fetchCurrentUser: async () => {
        try {
            const currentUser = await getCurrentUser();
            set({ currentUser, initialized: true });
        } catch (error) {
            if (error instanceof HttpClientError && error.status !== 401) {
                set({ initialized: true });
                throw error;
            }
            notifySessionResetHandlers();
            set({ currentUser: null, initialized: true });
        }
    },
    loginWithPassword: async (command) => {
        const currentUser = await login(command);
        set({ currentUser, initialized: true });
        return currentUser;
    },
    logoutCurrentUser: async () => {
        await logout();
        get().clearSession();
    },
    clearSession: () => {
        notifySessionResetHandlers();
        set({ currentUser: null, initialized: true });
    },
    reset: () => {
        notifySessionResetHandlers();
        set(initialState);
    },
}));

export function resetSessionStore() {
    useSessionStore.getState().reset();
}

function notifySessionResetHandlers() {
    for (const handler of sessionResetHandlers) {
        handler();
    }
}
