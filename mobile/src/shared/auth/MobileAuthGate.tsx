import { DotLoading, ErrorBlock } from "antd-mobile";
import { UNAUTHENTICATED_EVENT, errorMessage } from "@archive-management/frontend-core/api";
import { useSessionStore } from "@archive-management/frontend-core/auth";
import { useEffect, useMemo, useState } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router";

export function MobileAuthGate() {
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
            <main className="am-mobile-auth-state">
                <span>
                    正在校验登录状态
                    <DotLoading />
                </span>
            </main>
        );
    }

    if (authError) {
        return (
            <main className="am-mobile-auth-state">
                <ErrorBlock description={errorMessage(authError, "会话校验失败")} status="busy" />
            </main>
        );
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
