import { Spin, Typography } from "antd";
import { UNAUTHENTICATED_EVENT, errorMessage } from "@archive-management/frontend-core/api";
import { useSessionStore } from "@archive-management/frontend-core/authentication";
import { useEffect, useMemo, useState } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router";

export function AuthenticationGate() {
    const location = useLocation();
    const navigate = useNavigate();
    const initialized = useSessionStore((state) => state.initialized);
    const currentUser = useSessionStore((state) => state.currentUser);
    const fetchCurrentUser = useSessionStore((state) => state.fetchCurrentUser);
    const clearSession = useSessionStore((state) => state.clearSession);
    const [authenticationError, setAuthenticationError] = useState<unknown>();

    useEffect(() => {
        if (!initialized) {
            setAuthenticationError(undefined);
            void fetchCurrentUser().catch(setAuthenticationError);
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
            <main className="am-authentication-loading">
                <Spin />
                <Typography.Text>正在校验登录状态</Typography.Text>
            </main>
        );
    }

    if (authenticationError) {
        return <AuthenticationError error={authenticationError} />;
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

export function AuthenticationError({ error }: { error: unknown }) {
    return (
        <main className="am-authentication-loading">
            <Typography.Text type="danger">{errorMessage(error, "会话校验失败")}</Typography.Text>
        </main>
    );
}
