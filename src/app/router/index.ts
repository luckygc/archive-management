import { ElMessage } from "element-plus";
import { createRouter, createWebHistory } from "vue-router";
import { UNAUTHENTICATED_EVENT, errorMessage } from "../../shared/api/client";
import { useSessionStore } from "../stores/session";
import { routes } from "./routes";

const router = createRouter({
    history: createWebHistory(),
    routes,
});

router.beforeEach(async (to) => {
    const sessionStore = useSessionStore();
    if (!sessionStore.initialized) {
        try {
            await sessionStore.fetchCurrentUser();
        } catch (error) {
            ElMessage.error(errorMessage(error, "会话校验失败，请稍后重试"));
            return to.name === "Login";
        }
    }

    const isLoginRoute = to.name === "Login";
    if (!sessionStore.currentUser && !isLoginRoute) {
        return {
            name: "Login",
            query: {
                redirect: to.fullPath,
            },
        };
    }

    if (sessionStore.currentUser && isLoginRoute) {
        const redirect = normalizeRedirect(to.query.redirect);
        return redirect;
    }

    return true;
});

window.addEventListener(UNAUTHENTICATED_EVENT, () => {
    const sessionStore = useSessionStore();
    sessionStore.clearSession();
    if (router.currentRoute.value.name === "Login") {
        return;
    }
    void router.push({
        name: "Login",
        query: {
            redirect: router.currentRoute.value.fullPath,
        },
    });
});

function normalizeRedirect(value: unknown) {
    if (typeof value !== "string" || !value.startsWith("/")) {
        return "/";
    }
    if (value.startsWith("//")) {
        return "/";
    }
    return value;
}

export default router;
