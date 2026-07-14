import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import AuthenticationUsersPage from "./AuthenticationUsersPage.vue";

const archiveApiMocks = vi.hoisted(() => ({
    createAuthenticationUser: vi.fn(),
    getAuthenticationUser: vi.fn(),
    listAuthenticationUsers: vi.fn(),
    listAuthorizationRoles: vi.fn(),
    listOrganizationDepartments: vi.fn(),
    resetAuthenticationUserPassword: vi.fn(),
    saveAuthenticationUserRoles: vi.fn(),
    updateAuthenticationUser: vi.fn(),
}));

vi.mock("@/shared/api/authentication", () => archiveApiMocks);
vi.mock("@/shared/api/authorization", () => archiveApiMocks);
vi.mock("@/shared/api/organization", () => archiveApiMocks);

beforeEach(() => {
    archiveApiMocks.listAuthenticationUsers.mockResolvedValue({
        items: [
            {
                id: 7,
                username: "zhangsan",
                displayName: "张三",
                departmentCode: "D001",
                departmentName: "综合部",
                enabled: true,
                createdAt: "2026-07-03T00:00:00",
            },
        ],
    });
    archiveApiMocks.listAuthorizationRoles.mockResolvedValue({
        items: [{ id: 1, roleName: "档案管理员", enabled: true, createdAt: "2026-07-03T00:00:00" }],
    });
    archiveApiMocks.listOrganizationDepartments.mockResolvedValue({
        items: [
            {
                id: 1,
                departmentCode: "D001",
                departmentName: "综合部",
                enabled: true,
                sortOrder: 0,
                createdAt: "2026-07-03T00:00:00",
                updatedAt: "2026-07-03T00:00:00",
            },
        ],
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("AuthenticationUsersPage", () => {
    it("用户管理加载角色和部门目录以支持完整编辑与角色分配", async () => {
        render(AuthenticationUsersPage, { global: { plugins: [ElementPlus] } });

        await waitFor(() => {
            expect(archiveApiMocks.listAuthorizationRoles).toHaveBeenCalledWith(true, 1000);
            expect(archiveApiMocks.listOrganizationDepartments).toHaveBeenCalledWith(true);
        });
    });

    it("更新用户时保留清空的可选文本字段", async () => {
        archiveApiMocks.getAuthenticationUser.mockResolvedValue({
            id: 7,
            username: "zhangsan",
            displayName: "张三",
            email: "old@example.com",
            mobilePhone: "13800138000",
            departmentId: 1,
            departmentCode: "D001",
            departmentName: "综合部",
            enabled: true,
            createdAt: "2026-07-03T00:00:00",
            roles: [],
        });
        archiveApiMocks.updateAuthenticationUser.mockResolvedValue({
            id: 7,
            username: "zhangsan",
            displayName: "张三",
            mobilePhone: "13800138000",
            departmentId: 1,
            departmentCode: "D001",
            departmentName: "综合部",
            enabled: true,
            createdAt: "2026-07-03T00:00:00",
        });
        render(AuthenticationUsersPage, { global: { plugins: [ElementPlus] } });

        await fireEvent.click(await screen.findByRole("button", { name: "编辑" }));
        await waitFor(() => expect(archiveApiMocks.getAuthenticationUser).toHaveBeenCalledWith(7));
        const email = await screen.findByLabelText("邮箱");
        await fireEvent.update(email, "");
        await fireEvent.click(screen.getByRole("button", { name: "确定" }));

        await waitFor(() =>
            expect(archiveApiMocks.updateAuthenticationUser).toHaveBeenCalledWith(
                7,
                expect.objectContaining({ email: "" }),
            ),
        );
    });

    it("角色详情加载失败时不保存空角色", async () => {
        archiveApiMocks.getAuthenticationUser.mockRejectedValue(new Error("网络错误"));
        render(AuthenticationUsersPage, { global: { plugins: [ElementPlus] } });

        await fireEvent.click(await screen.findByRole("button", { name: "角色" }));
        expect(await screen.findByText("D001 综合部")).toBeInTheDocument();
        await waitFor(() => expect(archiveApiMocks.getAuthenticationUser).toHaveBeenCalledWith(7));
        const okButton = screen.getByRole("button", { name: "确定" });
        expect(okButton).toBeDisabled();
        await fireEvent.click(okButton);
        expect(archiveApiMocks.saveAuthenticationUserRoles).not.toHaveBeenCalled();
    });
});
