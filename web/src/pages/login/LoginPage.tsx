import { Button, Card, Form, Input, Typography } from "antd";
import { errorMessage } from "@archive-management/frontend-core/api";
import {
    capWidgetApiEndpoint,
    setCapChallengeContext,
    useCapVerification,
} from "@archive-management/frontend-core/cap";
import { useSessionStore } from "@archive-management/frontend-core/authentication";
import { createElement, forwardRef, useEffect, useRef, useState } from "react";
import type { HTMLAttributes } from "react";
import { useLocation, useNavigate } from "react-router";
import type { CapWidget } from "cap-widget";

interface LoginFormValues {
    username: string;
    password: string;
}

export function LoginPage() {
    const [form] = Form.useForm<LoginFormValues>();
    const location = useLocation();
    const navigate = useNavigate();
    const capWidgetRef = useRef<CapWidget | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [loginError, setLoginError] = useState("");
    const watchedUsername = Form.useWatch("username", form);
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
        setCapChallengeContext({
            username: watchedUsername?.trim(),
        });
    }, [watchedUsername]);

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
            setLoginError("请输入账号和密码");
            return;
        }
        if (!powToken) {
            setLoginError("请先完成安全验证");
            return;
        }

        setSubmitting(true);
        setLoginError("");
        try {
            await loginWithPassword({ username, password, powToken });
            await navigate(redirectTarget(location.search), { replace: true });
        } catch (error) {
            setLoginError(errorMessage(error, "登录失败"));
            resetCapWidget("请重新完成安全验证");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <main className="am-login">
            <Card className="am-login__panel">
                <Typography.Title level={1}>账号登录</Typography.Title>
                <Typography.Paragraph type="secondary">进入档案业务工作台</Typography.Paragraph>
                <Form form={form} layout="vertical" onFinish={(values) => void submitLogin(values)}>
                    <Form.Item label="账号" name="username">
                        <Input autoComplete="username" />
                    </Form.Item>
                    <Form.Item label="密码" name="password">
                        <Input.Password
                            autoComplete="current-password"
                            onPressEnter={form.submit}
                        />
                    </Form.Item>
                    <div className="am-login__pow">
                        <CapWidgetElement
                            ref={capWidgetRef}
                            data-cap-api-endpoint={capWidgetApiEndpoint()}
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
                        <Typography.Text type={powToken ? "success" : "secondary"}>
                            {securityMessage}
                        </Typography.Text>
                    </div>
                    {loginError ? (
                        <Typography.Text type="danger">{loginError}</Typography.Text>
                    ) : null}
                    <Button block htmlType="submit" loading={submitting} type="primary">
                        登录系统
                    </Button>
                </Form>
            </Card>
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
