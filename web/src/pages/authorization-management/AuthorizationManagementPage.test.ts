import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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

vi.mock("@/shared/api/archive", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/shared/api/archive")>()),
    ...archiveApiMocks,
}));

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
        renderPage();
        await selectCurrentSubject("档案管理员");

        await waitFor(() => {
            expect(archiveApiMocks.getRolePermissions).toHaveBeenCalledWith(2);
            expect(archiveApiMocks.getRoleArchiveDataScopes).toHaveBeenCalledWith(2);
        });
        expect((await screen.findAllByText("读取档案")).length).toBeGreaterThan(0);
        expect((await screen.findAllByText("全部档案")).length).toBeGreaterThan(0);
    });

    it("选择部门后仅加载部门数据范围", async () => {
        renderPage();
        await fireEvent.click(await screen.findByText("部门"));
        await selectCurrentSubject("D003 综合部");

        await waitFor(() =>
            expect(archiveApiMocks.getDepartmentArchiveDataScopes).toHaveBeenCalledWith(3),
        );
        expect((await screen.findAllByText("全部档案")).length).toBeGreaterThan(0);
    });

    it("用户某个角色权限加载失败时仍保留其他角色的权限", async () => {
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
        archiveApiMocks.getAuthenticationUser.mockResolvedValue({
            id: 5,
            username: "zhangsan",
            displayName: "张三",
            enabled: true,
            createdAt: "2026-07-03",
            roles: [
                { id: 10, roleName: "角色 A" },
                { id: 11, roleName: "角色 B" },
            ],
        });
        archiveApiMocks.getUserArchiveDataScopes.mockResolvedValue({ userId: 5, scopeIds: [] });
        archiveApiMocks.getRolePermissions.mockImplementation((roleId: number) =>
            roleId === 10
                ? Promise.reject(new Error("角色权限不可用"))
                : Promise.resolve({ roleId, permissionCodes: ["archive:item:read"] }),
        );

        renderPage();
        await fireEvent.click(await screen.findByText("用户"));
        await selectCurrentSubject("张三（zhangsan）");

        expect((await screen.findAllByText("读取档案")).length).toBeGreaterThan(0);
        expect(await screen.findByText("角色 A")).toBeInTheDocument();
        expect(await screen.findByText("角色 B")).toBeInTheDocument();
    });
});

function renderPage() {
    return render(AuthorizationManagementPage, { global: { plugins: [ElementPlus] } });
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
