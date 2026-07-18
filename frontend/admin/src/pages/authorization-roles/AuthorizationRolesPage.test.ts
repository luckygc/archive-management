import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import AuthorizationRolesPage from "./AuthorizationRolesPage.vue";

const archiveApiMocks = vi.hoisted(() => ({
    createAuthorizationRole: vi.fn(),
    deleteAuthorizationRole: vi.fn(),
    getRolePermissions: vi.fn(),
    listAuthorizationPermissions: vi.fn(),
    listAuthorizationRoles: vi.fn(),
    saveRolePermissions: vi.fn(),
    updateAuthorizationRole: vi.fn(),
}));

vi.mock("@/shared/api/authorization", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/shared/api/authorization")>()),
    ...archiveApiMocks,
}));

beforeEach(() => {
    archiveApiMocks.listAuthorizationRoles.mockResolvedValue({
        items: [{ id: 2, roleName: "档案管理员", enabled: true, createdAt: "2026-07-03T00:00:00" }],
    });
    archiveApiMocks.listAuthorizationPermissions.mockResolvedValue({ items: [] });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("AuthorizationRolesPage", () => {
    it("权限详情加载失败时不保存空权限", async () => {
        archiveApiMocks.getRolePermissions.mockRejectedValue(new Error("网络错误"));
        render(AuthorizationRolesPage, { global: { plugins: [ElementPlus] } });

        await fireEvent.click(await screen.findByRole("button", { name: "权限" }));
        await waitFor(() => expect(archiveApiMocks.getRolePermissions).toHaveBeenCalledWith(2));
        const okButton = screen.getByRole("button", { name: "确定" });
        expect(okButton).toBeDisabled();
        await fireEvent.click(okButton);
        expect(archiveApiMocks.saveRolePermissions).not.toHaveBeenCalled();
    });
});
