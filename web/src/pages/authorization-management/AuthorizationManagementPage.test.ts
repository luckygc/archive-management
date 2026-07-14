import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissionStore } from "@/stores/permissionStore";
import AuthorizationManagementPage from "./AuthorizationManagementPage.vue";

const archiveApiMocks = vi.hoisted(() => ({
    getAuthenticationUser: vi.fn(),
    getDepartmentArchiveDataScopes: vi.fn(),
    getRoleArchiveDataScopes: vi.fn(),
    getRolePermissions: vi.fn(),
    getUserArchiveDataScopes: vi.fn(),
    listArchiveDataScopes: vi.fn(),
    listAuthenticationUsers: vi.fn(),
    listAuthorizationPermissions: vi.fn(),
    listAuthorizationRoles: vi.fn(),
    listOrganizationDepartments: vi.fn(),
    saveDepartmentArchiveDataScopes: vi.fn(),
    saveRoleArchiveDataScopes: vi.fn(),
    saveRolePermissions: vi.fn(),
    saveUserArchiveDataScopes: vi.fn(),
}));

vi.mock("@/shared/api/authentication", () => archiveApiMocks);
vi.mock("@/shared/api/authorization", () => archiveApiMocks);
vi.mock("@/shared/api/organization", () => archiveApiMocks);

beforeEach(() => {
    archiveApiMocks.listAuthorizationRoles.mockResolvedValue({
        items: [{ id: 2, roleName: "档案管理员", enabled: true, createdAt: "2026-07-03" }],
    });
    archiveApiMocks.listAuthenticationUsers.mockResolvedValue({ items: [] });
    archiveApiMocks.listOrganizationDepartments.mockResolvedValue({
        items: [
            {
                id: 3,
                departmentCode: "D003",
                departmentName: "综合部",
                enabled: true,
                sortOrder: 0,
                createdAt: "2026-07-03",
                updatedAt: "2026-07-03",
            },
        ],
    });
    archiveApiMocks.getRolePermissions.mockResolvedValue({
        roleId: 2,
        permissionCodes: ["archive:item:read"],
    });
    archiveApiMocks.getRoleArchiveDataScopes.mockResolvedValue({ roleId: 2, scopeIds: [1] });
    archiveApiMocks.getDepartmentArchiveDataScopes.mockResolvedValue({
        departmentId: 3,
        scopeIds: [1],
    });
    archiveApiMocks.listAuthorizationPermissions.mockResolvedValue({
        items: [permission("archive:item:read", "读取档案")],
    });
    archiveApiMocks.listArchiveDataScopes.mockResolvedValue({
        items: [
            {
                id: 1,
                scopeCode: "*",
                scopeName: "全部档案",
                scopeType: "ALL",
                dimensions: [],
                enabled: true,
            },
        ],
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("AuthorizationManagementPage", () => {
    it("选择角色后加载并展示权限和数据范围", async () => {
        renderPage(["authorization:permission:manage", "archive:data-scope:manage"]);
        await selectCurrentSubject("档案管理员");

        await waitFor(() => {
            expect(archiveApiMocks.getRolePermissions).toHaveBeenCalledWith(2);
            expect(archiveApiMocks.getRoleArchiveDataScopes).toHaveBeenCalledWith(2);
        });
        expect((await screen.findAllByText("读取档案")).length).toBeGreaterThan(0);
        expect((await screen.findAllByText("全部档案")).length).toBeGreaterThan(0);
    });

    it("仅功能权限管理员只加载和呈现角色功能权限", async () => {
        renderPage(["authorization:permission:manage"]);
        await selectCurrentSubject("档案管理员");

        await waitFor(() => expect(archiveApiMocks.getRolePermissions).toHaveBeenCalledWith(2));
        expect(archiveApiMocks.listAuthorizationPermissions).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listAuthenticationUsers).not.toHaveBeenCalled();
        expect(archiveApiMocks.listOrganizationDepartments).not.toHaveBeenCalled();
        expect(archiveApiMocks.listArchiveDataScopes).not.toHaveBeenCalled();
        expect(archiveApiMocks.getRoleArchiveDataScopes).not.toHaveBeenCalled();
        expect(screen.getByText("功能权限")).toBeInTheDocument();
        expect(screen.queryByText("数据范围")).not.toBeInTheDocument();
    });

    it("仅数据范围管理员只加载和呈现主体范围功能", async () => {
        archiveApiMocks.listAuthenticationUsers.mockResolvedValue({
            items: [
                {
                    id: 5,
                    username: "zhangsan",
                    displayName: "张三",
                    enabled: true,
                    createdAt: "2026-07-03",
                },
            ],
        });
        archiveApiMocks.getUserArchiveDataScopes.mockResolvedValue({ userId: 5, scopeIds: [] });

        renderPage(["archive:data-scope:manage"]);
        await fireEvent.click(await screen.findByText("部门"));
        await selectCurrentSubject("D003 综合部");

        await waitFor(() =>
            expect(archiveApiMocks.getDepartmentArchiveDataScopes).toHaveBeenCalledWith(3),
        );
        expect(archiveApiMocks.listAuthorizationRoles).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listAuthenticationUsers).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listOrganizationDepartments).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listArchiveDataScopes).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listAuthorizationPermissions).not.toHaveBeenCalled();
        expect(archiveApiMocks.getRolePermissions).not.toHaveBeenCalled();
        expect(screen.queryByText("功能权限")).not.toBeInTheDocument();
        expect(screen.getByText("数据范围")).toBeInTheDocument();
    });
});

function renderPage(permissionCodes: string[]) {
    const pinia = createPinia();
    setActivePinia(pinia);
    usePermissionStore().permissionCodes = permissionCodes;
    return render(AuthorizationManagementPage, {
        global: { plugins: [ElementPlus, pinia] },
    });
}

async function selectCurrentSubject(option: string) {
    const selector = await screen.findByRole("combobox");
    await fireEvent.click(selector);
    await fireEvent.click(await screen.findByText(option));
}

function permission(permissionCode: string, permissionName: string) {
    return {
        permissionCode,
        permissionName,
        moduleCode: "archive",
        description: permissionName,
    };
}
