import { createPinia, setActivePinia } from "pinia";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { nextTick } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { HttpClientError } from "@archive-management/frontend-core/api";

import { useSessionStore } from "@/stores/sessionStore";

import LoginPage from "./LoginPage.vue";

const mocks = vi.hoisted(() => ({
    fetchPermissions: vi.fn(),
    login: vi.fn(),
    replace: vi.fn(),
    verifyTotp: vi.fn(),
}));

vi.mock("vue-router", () => ({
    useRouter: () => ({ replace: mocks.replace }),
}));

vi.mock("@archive-management/frontend-core/api", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@archive-management/frontend-core/api")>()),
    login: mocks.login,
    verifyTotpLoginChallenge: mocks.verifyTotp,
}));

vi.mock("@/shared/api/authorization", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/shared/api/authorization")>()),
    getCurrentUserPermissions: mocks.fetchPermissions,
}));

describe("LoginPage", () => {
    beforeEach(() => {
        const pinia = createPinia();
        setActivePinia(pinia);
        mocks.login.mockReset();
        mocks.replace.mockReset();
        mocks.verifyTotp.mockReset();
        mocks.fetchPermissions.mockReset();
        mocks.fetchPermissions.mockResolvedValue({ permissionCodes: [] });
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it("缺少账号密码时给出明确错误", async () => {
        render(LoginPage, { global: { plugins: [ElementPlus, createPinia()] } });

        await fireEvent.click(screen.getByRole("button", { name: "登录系统" }));

        expect(screen.getByRole("alert")).toHaveTextContent("请输入账号和密码");
        expect(mocks.login).not.toHaveBeenCalled();
    });

    it("完成安全验证后提交登录并进入工作台", async () => {
        const pinia = createPinia();
        setActivePinia(pinia);
        mocks.login.mockResolvedValue({
            status: 200,
            session: {
                sessionId: "session-1",
                username: "admin",
                displayName: "管理员",
                roles: ["admin"],
            },
        });
        const { container } = render(LoginPage, { global: { plugins: [ElementPlus, pinia] } });
        const inputs = container.querySelectorAll("input");

        await fireEvent.update(inputs[0], "admin");
        await fireEvent.update(inputs[1], "secret");
        await fireEvent(
            container.querySelector("cap-widget")!,
            new CustomEvent("solve", { detail: { token: "pow-token" } }),
        );
        await fireEvent.click(screen.getByRole("button", { name: "登录系统" }));

        await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/"));
        expect(mocks.login).toHaveBeenCalledWith({
            username: "admin",
            password: "secret",
            powToken: "pow-token",
        });
    });

    it("密码输入回车只触发一次第一阶段登录请求", async () => {
        const pending = deferred<{
            status: 200;
            session: { sessionId: string; username: string; displayName: string; roles: string[] };
        }>();
        mocks.login.mockReturnValue(pending.promise);
        const pinia = createPinia();
        setActivePinia(pinia);
        const { container } = render(LoginPage, { global: { plugins: [ElementPlus, pinia] } });
        installCapWidgetReset(container);
        const inputs = container.querySelectorAll("input");
        await fireEvent.update(inputs[0], "admin");
        await fireEvent.update(inputs[1], "secret");
        await fireEvent(
            container.querySelector("cap-widget")!,
            new CustomEvent("solve", { detail: { token: "pow-token" } }),
        );

        await fireEvent.keyUp(inputs[1], { key: "Enter" });
        await fireEvent.submit(container.querySelector("form")!);
        await fireEvent.submit(container.querySelector("form")!);

        expect(mocks.login).toHaveBeenCalledTimes(1);
        pending.resolve({
            status: 200,
            session: {
                sessionId: "session-1",
                username: "admin",
                displayName: "管理员",
                roles: ["admin"],
            },
        });
        await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/"));
    });

    it("仅在服务端要求时进入 TOTP 步骤且不提前导航", async () => {
        mocks.login.mockResolvedValue({
            status: 202,
            challenge: { challengeToken: "challenge-1", expiresAt: "2099-08-09T10:05:00Z" },
        });
        const pinia = createPinia();
        setActivePinia(pinia);
        const { container } = render(LoginPage, { global: { plugins: [ElementPlus, pinia] } });
        installCapWidgetReset(container);
        const inputs = container.querySelectorAll("input");

        await fireEvent.update(inputs[0], "admin");
        await fireEvent.update(inputs[1], "secret");
        await fireEvent(
            container.querySelector("cap-widget")!,
            new CustomEvent("solve", { detail: { token: "pow-token" } }),
        );
        await fireEvent.click(screen.getByRole("button", { name: "登录系统" }));

        expect(await screen.findByRole("heading", { name: "二次验证" })).toBeInTheDocument();
        expect(mocks.replace).not.toHaveBeenCalled();
        expect(useSessionStore().currentUser).toBeNull();
        expect(inputs[1]).toHaveValue("");
        expect(screen.getByLabelText("验证码")).toHaveAttribute("autocomplete", "one-time-code");
        expect(screen.getByLabelText("验证码")).toHaveAttribute("inputmode", "numeric");
    });

    it("明确提交正确 TOTP 后才进入工作台", async () => {
        mocks.login.mockResolvedValue({
            status: 202,
            challenge: { challengeToken: "challenge-1", expiresAt: "2099-08-09T10:05:00Z" },
        });
        mocks.verifyTotp.mockResolvedValue({
            sessionId: "session-1",
            username: "admin",
            displayName: "管理员",
            roles: ["admin"],
        });
        const pinia = createPinia();
        setActivePinia(pinia);
        const { container } = render(LoginPage, { global: { plugins: [ElementPlus, pinia] } });
        await completeFirstStep(container);

        await fireEvent.update(screen.getByLabelText("验证码"), "123456");
        expect(mocks.verifyTotp).not.toHaveBeenCalled();
        await fireEvent.click(screen.getByRole("button", { name: "验证并登录" }));

        await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/"));
        expect(mocks.verifyTotp).toHaveBeenCalledWith({
            challengeToken: "challenge-1",
            code: "123456",
        });
    });

    it("验证码输入回车只触发一次二次验证请求", async () => {
        mocks.login.mockResolvedValue({
            status: 202,
            challenge: { challengeToken: "challenge-1", expiresAt: "2099-08-09T10:05:00Z" },
        });
        const pending = deferred<{
            sessionId: string;
            username: string;
            displayName: string;
            roles: string[];
        }>();
        mocks.verifyTotp.mockReturnValue(pending.promise);
        const pinia = createPinia();
        setActivePinia(pinia);
        const { container } = render(LoginPage, { global: { plugins: [ElementPlus, pinia] } });
        await completeFirstStep(container);
        const codeInput = screen.getByLabelText("验证码");
        const form = codeInput.closest("form")!;
        await fireEvent.update(codeInput, "123456");

        await fireEvent.keyUp(codeInput, { key: "Enter" });
        await fireEvent.submit(form);
        await fireEvent.submit(form);

        expect(mocks.verifyTotp).toHaveBeenCalledTimes(1);
        pending.resolve({
            sessionId: "session-1",
            username: "admin",
            displayName: "管理员",
            roles: ["admin"],
        });
        await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/"));
    });

    it("验证码错误时保留第二步并清空验证码", async () => {
        mocks.login.mockResolvedValue({
            status: 202,
            challenge: { challengeToken: "challenge-1", expiresAt: "2099-08-09T10:05:00Z" },
        });
        mocks.verifyTotp.mockRejectedValue(
            new HttpClientError("账号或凭证错误", 401, "TOTP_CODE_INVALID"),
        );
        const pinia = createPinia();
        setActivePinia(pinia);
        const { container } = render(LoginPage, { global: { plugins: [ElementPlus, pinia] } });
        await completeFirstStep(container);

        const codeInput = screen.getByLabelText("验证码");
        await fireEvent.update(codeInput, "123456");
        await fireEvent.click(screen.getByRole("button", { name: "验证并登录" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("账号或凭证错误");
        expect(screen.getByRole("heading", { name: "二次验证" })).toBeInTheDocument();
        expect(codeInput).toHaveValue("");
        expect(mocks.replace).not.toHaveBeenCalled();
    });

    it("挑战失效时返回第一步并要求重新完成 CAP", async () => {
        mocks.login.mockResolvedValue({
            status: 202,
            challenge: { challengeToken: "challenge-1", expiresAt: "2099-08-09T10:05:00Z" },
        });
        mocks.verifyTotp.mockRejectedValue(
            new HttpClientError("二次验证已失效", 401, "TOTP_CHALLENGE_INVALID"),
        );
        const pinia = createPinia();
        setActivePinia(pinia);
        const { container } = render(LoginPage, { global: { plugins: [ElementPlus, pinia] } });
        await completeFirstStep(container);

        await fireEvent.update(screen.getByLabelText("验证码"), "123456");
        await fireEvent.click(screen.getByRole("button", { name: "验证并登录" }));

        expect(await screen.findByRole("heading", { name: "账号登录" })).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent("二次验证已失效，请重新登录");
        expect(screen.getByText("请重新完成安全验证")).toBeInTheDocument();
    });

    it("本地检测到挑战过期时清理第二步并返回账号登录", async () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-08-09T10:00:00Z"));
        mocks.login.mockResolvedValue({
            status: 202,
            challenge: { challengeToken: "challenge-1", expiresAt: "2026-08-09T10:00:01Z" },
        });
        const pinia = createPinia();
        setActivePinia(pinia);
        const { container } = render(LoginPage, { global: { plugins: [ElementPlus, pinia] } });
        await completeFirstStep(container);

        vi.advanceTimersByTime(1_001);
        await nextTick();

        expect(screen.getByRole("heading", { name: "账号登录" })).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent("二次验证已过期，请重新登录");
        expect(useSessionStore().currentUser).toBeNull();
    });
});

async function completeFirstStep(container: Element) {
    installCapWidgetReset(container);
    const inputs = container.querySelectorAll("input");
    await fireEvent.update(inputs[0], "admin");
    await fireEvent.update(inputs[1], "secret");
    await fireEvent(
        container.querySelector("cap-widget")!,
        new CustomEvent("solve", { detail: { token: "pow-token" } }),
    );
    await fireEvent.click(screen.getByRole("button", { name: "登录系统" }));
    await screen.findByRole("heading", { name: "二次验证" });
}

function installCapWidgetReset(container: Element) {
    const widget = container.querySelector("cap-widget");
    if (widget && !("reset" in widget)) Object.assign(widget, { reset: vi.fn() });
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((resolvePromise) => {
        resolve = resolvePromise;
    });
    return { promise, resolve };
}
