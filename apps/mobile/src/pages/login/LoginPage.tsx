import { Button, Form, Input, Toast } from "antd-mobile";
import { errorMessage } from "@archive-management/app-core/api";
import { useCapVerification } from "@archive-management/app-core/cap";
import { useSessionStore } from "@archive-management/app-core/auth";
import { createElement, forwardRef, useEffect, useRef, useState } from "react";
import type { HTMLAttributes } from "react";
import { useLocation, useNavigate } from "react-router";
import type { CapWidget } from "cap-widget";

interface LoginFormValues {
    username?: string;
    password?: string;
}

export function LoginPage() {
    const location = useLocation();
    const navigate = useNavigate();
    const capWidgetRef = useRef<CapWidget | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const loginWithPassword = useSessionStore((state) => state.loginWithPassword);
    const currentUser = useSessionStore((state) => state.currentUser);
    const {
        powToken,
        securityMessage,
        resetCapWidget,
        handleCapSolve,
        handleCapReset,
        handleCapError,
    } = useCapVerification(capWidgetRef);

    useEffect(() => {
        const widget = capWidgetRef.current;
        if (!widget) {
            return undefined;
        }
        widget.addEventListener("solve", handleCapSolve);
        widget.addEventListener("reset", handleCapReset);
        widget.addEventListener("error", handleCapError);
        return () => {
            widget.removeEventListener("solve", handleCapSolve);
            widget.removeEventListener("reset", handleCapReset);
            widget.removeEventListener("error", handleCapError);
        };
    }, [handleCapError, handleCapReset, handleCapSolve]);

    useEffect(() => {
        if (currentUser) {
            void navigate(redirectTarget(location.search), { replace: true });
        }
    }, [currentUser, location.search, navigate]);

    async function submitLogin(values: LoginFormValues) {
        const username = values.username?.trim();
        const password = values.password;
        if (!username || !password) {
            Toast.show("请输入账号和密码");
            return;
        }
        if (!powToken) {
            Toast.show("请先完成安全验证");
            return;
        }

        setSubmitting(true);
        try {
            await loginWithPassword({ username, password, powToken });
            await navigate(redirectTarget(location.search), { replace: true });
        } catch (error) {
            Toast.show(errorMessage(error, "登录失败"));
            resetCapWidget("请重新完成安全验证");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <main className="am-mobile-login">
            <div className="am-mobile-login__title">
                <h1>档案移动门户</h1>
                <p>待办、审批和归档接收入口</p>
            </div>
            <Form
                footer={
                    <Button block color="primary" loading={submitting} type="submit">
                        登录
                    </Button>
                }
                layout="vertical"
                onFinish={(values) => void submitLogin(values as LoginFormValues)}
            >
                <Form.Item label="账号" name="username">
                    <Input autoComplete="username" clearable />
                </Form.Item>
                <Form.Item label="密码" name="password">
                    <Input autoComplete="current-password" clearable type="password" />
                </Form.Item>
                <div className="am-mobile-login__pow">
                    <CapWidgetElement
                        ref={capWidgetRef}
                        data-cap-api-endpoint="/api/v1/auth/cap/"
                        data-cap-hidden-field-name="powToken"
                        data-cap-i18n-error-aria-label="安全验证失败"
                        data-cap-i18n-error-label="验证失败"
                        data-cap-i18n-initial-state="点击完成安全验证"
                        data-cap-i18n-solved-label="安全验证已完成"
                        data-cap-i18n-verified-aria-label="安全验证已完成"
                        data-cap-i18n-verifying-aria-label="正在完成安全验证"
                        data-cap-i18n-verifying-label="正在验证..."
                        data-cap-i18n-verify-aria-label="完成安全验证"
                        data-cap-worker-count="2"
                        data-testid="cap-widget"
                        required
                    />
                    <span>{securityMessage}</span>
                </div>
            </Form>
        </main>
    );
}

type CapWidgetElementProps = HTMLAttributes<HTMLElement> & {
    "data-cap-api-endpoint": string;
    "data-cap-hidden-field-name": string;
    "data-cap-worker-count": string;
    "data-cap-i18n-initial-state": string;
    "data-cap-i18n-verifying-label": string;
    "data-cap-i18n-solved-label": string;
    "data-cap-i18n-error-label": string;
    "data-cap-i18n-verify-aria-label": string;
    "data-cap-i18n-verifying-aria-label": string;
    "data-cap-i18n-verified-aria-label": string;
    "data-cap-i18n-error-aria-label": string;
    required?: boolean;
};

const CapWidgetElement = forwardRef<CapWidget, CapWidgetElementProps>((props, ref) =>
    createElement("cap-widget", { ...props, ref }),
);

function redirectTarget(search: string) {
    const redirect = new URLSearchParams(search).get("redirect");
    if (!redirect || !redirect.startsWith("/") || redirect.startsWith("//")) {
        return "/";
    }
    return redirect;
}
