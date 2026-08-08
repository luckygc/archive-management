import { createPinia, setActivePinia } from "pinia";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus, { ElMessage } from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { HttpClientError } from "@archive-management/frontend-core/api";

import { useSessionStore } from "@/stores/sessionStore";

import AccountSecurityPage from "./AccountSecurityPage.vue";

const apiMocks = vi.hoisted(() => ({
    createTotpCredential: vi.fn(),
    createTotpEnrollment: vi.fn(),
    disableTotpCredential: vi.fn(),
    getCurrentUser: vi.fn(),
}));

vi.mock("@archive-management/frontend-core/api", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@archive-management/frontend-core/api")>()),
    ...apiMocks,
}));

describe("AccountSecurityPage", () => {
    beforeEach(() => {
        setActivePinia(createPinia());
        Object.values(apiMocks).forEach((mock) => mock.mockReset());
        apiMocks.createTotpEnrollment.mockResolvedValue({
            enrollmentToken: "enrollment-1",
            manualKey: "ABCDEF123456",
            otpauthUri:
                "otpauth://totp/Archive%20Management:reader?secret=ABCDEF123456&issuer=Archive%20Management",
            expiresAt: "2099-08-09T10:05:00Z",
        });
    });

    afterEach(() => {
        cleanup();
        ElMessage.closeAll();
        vi.restoreAllMocks();
    });

    it("未启用用户在本地生成二维码并同时展示手工密钥", async () => {
        renderPage(false);

        await fireEvent.click(screen.getByRole("button", { name: "启用身份验证器" }));

        const qrCode = await screen.findByRole("img", { name: "身份验证器配置二维码" });
        expect(qrCode.getAttribute("src")).toMatch(/^data:image\/svg\+xml;charset=utf-8,/);
        expect(screen.getByLabelText("手工密钥")).toHaveValue("ABCDEF123456");
        expect(apiMocks.createTotpEnrollment).toHaveBeenCalledTimes(1);
    });

    it("重复开始设置时只创建一个 enrollment", async () => {
        const pending = deferred<{
            enrollmentToken: string;
            manualKey: string;
            otpauthUri: string;
            expiresAt: string;
        }>();
        apiMocks.createTotpEnrollment.mockReturnValue(pending.promise);
        const { unmount } = renderPage(false);
        const button = screen.getByRole("button", { name: "启用身份验证器" });

        await Promise.all([fireEvent.click(button), fireEvent.click(button)]);

        expect(apiMocks.createTotpEnrollment).toHaveBeenCalledTimes(1);
        unmount();
        pending.resolve({
            enrollmentToken: "enrollment-1",
            manualKey: "ABCDEF123456",
            otpauthUri: "otpauth://totp/Archive%20Management:reader?secret=ABCDEF123456",
            expiresAt: "2099-08-09T10:05:00Z",
        });
        await pending.promise;
    });

    it("只有确认成功并刷新当前用户后才显示为已启用", async () => {
        const success = vi.spyOn(ElMessage, "success").mockImplementation(() => ({}) as never);
        apiMocks.createTotpCredential.mockResolvedValue({ totpEnabled: true });
        apiMocks.getCurrentUser.mockResolvedValue(currentUser(true));
        renderPage(false);
        await fireEvent.click(screen.getByRole("button", { name: "启用身份验证器" }));

        await fireEvent.update(screen.getByLabelText("当前密码"), "secret");
        await fireEvent.update(screen.getByLabelText("验证码"), "123456");
        await fireEvent.click(screen.getByRole("button", { name: "确认启用" }));

        await waitFor(() => expect(screen.getByText("已启用")).toBeInTheDocument());
        expect(apiMocks.createTotpCredential).toHaveBeenCalledWith({
            enrollmentToken: "enrollment-1",
            currentPassword: "secret",
            code: "123456",
        });
        expect(apiMocks.getCurrentUser).toHaveBeenCalledTimes(1);
        expect(screen.queryByDisplayValue("ABCDEF123456")).not.toBeInTheDocument();
        await waitFor(() => expect(success).toHaveBeenCalledWith("身份验证器已启用"));
    });

    it("提交期间锁定设置表单并在完成后收敛状态", async () => {
        const pending = deferred<{ totpEnabled: boolean }>();
        apiMocks.createTotpCredential.mockReturnValue(pending.promise);
        apiMocks.getCurrentUser.mockResolvedValue(currentUser(true));
        renderPage(false);
        await fireEvent.click(screen.getByRole("button", { name: "启用身份验证器" }));
        await fireEvent.update(screen.getByLabelText("当前密码"), "secret");
        const codeInput = screen.getByLabelText("验证码");
        const form = codeInput.closest("form")!;
        await fireEvent.update(codeInput, "123456");

        await fireEvent.keyUp(codeInput, { key: "Enter" });
        await fireEvent.submit(form);
        await fireEvent.submit(form);

        expect(apiMocks.createTotpCredential).toHaveBeenCalledTimes(1);
        expect(screen.getByRole("button", { name: "确认启用" })).toBeDisabled();
        expect(screen.getByLabelText("当前密码")).toBeDisabled();
        expect(screen.getByLabelText("验证码")).toBeDisabled();

        pending.resolve({ totpEnabled: true });
        await waitFor(() => expect(screen.getByText("已启用")).toBeInTheDocument());
    });

    it("enrollment 失效时清除一次性密钥并提示重新开始", async () => {
        apiMocks.createTotpCredential.mockRejectedValue(
            new HttpClientError("设置凭据无效", 400, "TOTP_ENROLLMENT_INVALID"),
        );
        renderPage(false);
        await fireEvent.click(screen.getByRole("button", { name: "启用身份验证器" }));
        await fireEvent.update(screen.getByLabelText("当前密码"), "secret");
        await fireEvent.update(screen.getByLabelText("验证码"), "123456");

        await fireEvent.click(screen.getByRole("button", { name: "确认启用" }));

        expect(await screen.findByText("设置已过期，请重新开始")).toHaveAttribute("role", "alert");
        expect(screen.queryByDisplayValue("ABCDEF123456")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "启用身份验证器" })).toBeInTheDocument();
    });

    it("启用已成功但状态刷新失败时保留正确状态并给出恢复提示", async () => {
        const warning = vi.spyOn(ElMessage, "warning").mockImplementation(() => ({}) as never);
        apiMocks.createTotpCredential.mockResolvedValue({ totpEnabled: true });
        apiMocks.getCurrentUser.mockRejectedValue(new Error("会话查询暂不可用"));
        renderPage(false);
        await fireEvent.click(screen.getByRole("button", { name: "启用身份验证器" }));
        await fireEvent.update(screen.getByLabelText("当前密码"), "secret");
        await fireEvent.update(screen.getByLabelText("验证码"), "123456");

        await fireEvent.click(screen.getByRole("button", { name: "确认启用" }));

        await waitFor(() => expect(screen.getByText("已启用")).toBeInTheDocument());
        await waitFor(() =>
            expect(warning).toHaveBeenCalledWith(
                "身份验证器已启用，但状态刷新失败，请稍后重新打开页面确认",
            ),
        );
        expect(screen.queryByDisplayValue("ABCDEF123456")).not.toBeInTheDocument();
    });

    it("取消设置时立即移除密钥和二维码", async () => {
        renderPage(false);
        await fireEvent.click(screen.getByRole("button", { name: "启用身份验证器" }));
        await screen.findByRole("img", { name: "身份验证器配置二维码" });

        await fireEvent.click(screen.getByRole("button", { name: "取消" }));

        expect(screen.queryByRole("img", { name: "身份验证器配置二维码" })).not.toBeInTheDocument();
        expect(screen.queryByDisplayValue("ABCDEF123456")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "启用身份验证器" })).toBeInTheDocument();
    });

    it("停用时要求当前密码和验证码并刷新当前用户", async () => {
        const success = vi.spyOn(ElMessage, "success").mockImplementation(() => ({}) as never);
        const pending = deferred<void>();
        apiMocks.disableTotpCredential.mockReturnValue(pending.promise);
        apiMocks.getCurrentUser.mockResolvedValue(currentUser(false));
        renderPage(true);

        await fireEvent.click(screen.getByRole("button", { name: "停用身份验证器" }));
        await fireEvent.update(screen.getByLabelText("当前密码"), "secret");
        const codeInput = screen.getByLabelText("验证码");
        const form = codeInput.closest("form")!;
        await fireEvent.update(codeInput, "654321");

        await fireEvent.keyUp(codeInput, { key: "Enter" });
        await fireEvent.submit(form);
        await fireEvent.submit(form);

        expect(apiMocks.disableTotpCredential).toHaveBeenCalledTimes(1);
        pending.resolve();
        await waitFor(() => expect(screen.getByText("未启用")).toBeInTheDocument());
        expect(apiMocks.disableTotpCredential).toHaveBeenCalledWith({
            currentPassword: "secret",
            code: "654321",
        });
        await waitFor(() => expect(success).toHaveBeenCalledWith("身份验证器已停用"));
    });
});

function renderPage(totpEnabled: boolean) {
    const pinia = createPinia();
    setActivePinia(pinia);
    const sessionStore = useSessionStore();
    sessionStore.initialized = true;
    sessionStore.currentUser = currentUser(totpEnabled);
    return render(AccountSecurityPage, { global: { plugins: [ElementPlus, pinia] } });
}

function currentUser(totpEnabled: boolean) {
    return {
        sessionId: "session-1",
        username: "reader",
        displayName: "只读用户",
        roles: [],
        totpEnabled,
    };
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((resolvePromise) => {
        resolve = resolvePromise;
    });
    return { promise, resolve };
}
