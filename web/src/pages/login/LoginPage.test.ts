import { createPinia, setActivePinia } from "pinia";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import LoginPage from "./LoginPage.vue";

const mocks = vi.hoisted(() => ({
    fetchPermissions: vi.fn(),
    login: vi.fn(),
    replace: vi.fn(),
}));

vi.mock("vue-router", () => ({
    useRouter: () => ({ replace: mocks.replace }),
}));

vi.mock("@archive-management/frontend-core/api", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@archive-management/frontend-core/api")>()),
    login: mocks.login,
}));

vi.mock("@/shared/api/archive", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/shared/api/archive")>()),
    getCurrentUserPermissions: mocks.fetchPermissions,
}));

describe("LoginPage", () => {
    beforeEach(() => {
        const pinia = createPinia();
        setActivePinia(pinia);
        mocks.login.mockReset();
        mocks.replace.mockReset();
        mocks.fetchPermissions.mockReset();
        mocks.fetchPermissions.mockResolvedValue({ permissionCodes: [] });
    });

    afterEach(cleanup);

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
            sessionId: "session-1",
            username: "admin",
            displayName: "管理员",
            roles: ["admin"],
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
});
