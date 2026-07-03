import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { AuthenticationUsersPage } from "./AuthenticationUsersPage";

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

vi.mock("@/shared/api/archive", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@/shared/api/archive")>();
    return {
        ...actual,
        ...archiveApiMocks,
    };
});

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
    it("does not save empty roles when role detail loading fails", async () => {
        archiveApiMocks.getAuthenticationUser.mockRejectedValue(new Error("网络错误"));

        render(
            <QueryClientProvider client={new QueryClient()}>
                <AuthenticationUsersPage />
            </QueryClientProvider>,
        );

        fireEvent.click(await screen.findByRole("button", { name: /角\s*色/ }));
        expect(await screen.findByText("D001 综合部")).toBeInTheDocument();
        await waitFor(() => {
            expect(archiveApiMocks.getAuthenticationUser).toHaveBeenCalledWith(7);
        });

        const okButton = screen.getByRole("button", { name: "OK" });
        expect(okButton).toHaveAttribute("disabled");
        fireEvent.click(okButton);

        expect(archiveApiMocks.saveAuthenticationUserRoles).not.toHaveBeenCalled();
    });
});
