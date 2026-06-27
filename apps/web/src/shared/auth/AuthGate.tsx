import { Spin, Typography } from "antd";
import { UNAUTHENTICATED_EVENT, errorMessage } from "@archive-management/app-core/api";
import { useSessionStore } from "@archive-management/app-core/auth";
import { useEffect, useMemo, useState } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router";

export function AuthGate() {
    const location = useLocation();
    const navigate = useNavigate();
    const initialized = useSessionStore((state) => state.initialized);
    const currentUser = useSessionStore((state) => state.currentUser);
    const fetchCurrentUser = useSessionStore((state) => state.fetchCurrentUser);
    const clearSession = useSessionStore((state) => state.clearSession);
    const [authError, setAuthError] = useState<unknown>();

    useEffect(() => {
        if (!initialized) {
            setAuthError(undefined);
            void fetchCurrentUser().catch(setAuthError);
        }
    }, [fetchCurrentUser, initialized]);

    useEffect(() => {
        function handleUnauthenticated() {
            clearSession();
            if (location.pathname === "/login") {
                return;
            }
            void navigate(loginPath(location), { replace: true });
        }

        window.addEventListener(UNAUTHENTICATED_EVENT, handleUnauthenticated);
        return () => window.removeEventListener(UNAUTHENTICATED_EVENT, handleUnauthenticated);
    }, [clearSession, location, navigate]);

    const redirectPath = useMemo(() => loginPath(location), [location]);

    if (!initialized) {
        return (
            <main className="am-auth-loading">
                <Spin />
                <Typography.Text>正在校验登录状态</Typography.Text>
            </main>
        );
    }

    if (authError) {
        return <AuthError error={authError} />;
    }

    if (!currentUser) {
        return <Navigate replace to={redirectPath} />;
    }

    return <Outlet />;
}

function loginPath(location: ReturnType<typeof useLocation>) {
    const fullPath = `${location.pathname}${location.search}${location.hash}`;
    return `/login?redirect=${encodeURIComponent(fullPath)}`;
}

export function AuthError({ error }: { error: unknown }) {
    return (
        <main className="am-auth-loading">
            <Typography.Text type="danger">{errorMessage(error, "会话校验失败")}</Typography.Text>
        </main>
    );
}
