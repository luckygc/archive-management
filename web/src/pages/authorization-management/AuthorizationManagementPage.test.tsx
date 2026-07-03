import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { AuthorizationManagementPage } from "./AuthorizationManagementPage";

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

vi.mock("@/shared/api/archive", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@/shared/api/archive")>();
    return {
        ...actual,
        ...archiveApiMocks,
    };
});

beforeEach(() => {
    archiveApiMocks.listAuthorizationRoles.mockResolvedValue({
        items: [
            {
                id: 2,
                roleName: "档案管理员",
                enabled: true,
                createdAt: "2026-07-03T00:00:00",
            },
        ],
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
                createdAt: "2026-07-03T00:00:00",
                updatedAt: "2026-07-03T00:00:00",
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
        items: [
            {
                permissionCode: "archive:item:read",
                permissionName: "读取档案",
                moduleCode: "archive",
                description: "读取档案",
            },
        ],
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
    it("loads catalogs to display selected subject permissions and scopes", async () => {
        render(
            <QueryClientProvider client={new QueryClient()}>
                <AuthorizationManagementPage />
            </QueryClientProvider>,
        );

        const selector = await screen.findByRole("combobox");
        fireEvent.mouseDown(selector);
        fireEvent.click(selector);
        fireEvent.click(await screen.findByText("档案管理员"));

        await waitFor(() => {
            expect(archiveApiMocks.getRolePermissions).toHaveBeenCalledWith(2);
            expect(archiveApiMocks.getRoleArchiveDataScopes).toHaveBeenCalledWith(2);
            expect(archiveApiMocks.listAuthorizationPermissions).toHaveBeenCalled();
            expect(archiveApiMocks.listArchiveDataScopes).toHaveBeenCalled();
        });
        expect((await screen.findAllByText("读取档案")).length).toBeGreaterThan(0);
        expect((await screen.findAllByText("全部档案")).length).toBeGreaterThan(0);
    });

    it("loads department data scopes when department subject is selected", async () => {
        render(
            <QueryClientProvider client={new QueryClient()}>
                <AuthorizationManagementPage />
            </QueryClientProvider>,
        );

        fireEvent.click(await screen.findByText("部门"));
        const selector = await screen.findByRole("combobox");
        fireEvent.mouseDown(selector);
        fireEvent.click(await screen.findByText("D003 综合部"));

        await waitFor(() => {
            expect(archiveApiMocks.getDepartmentArchiveDataScopes).toHaveBeenCalledWith(3);
            expect(archiveApiMocks.listArchiveDataScopes).toHaveBeenCalled();
        });
        expect((await screen.findAllByText("全部档案")).length).toBeGreaterThan(0);
    });
});
