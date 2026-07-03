import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { AuthorizationRolesPage } from "./AuthorizationRolesPage";

const archiveApiMocks = vi.hoisted(() => ({
    createAuthorizationRole: vi.fn(),
    deleteAuthorizationRole: vi.fn(),
    getRolePermissions: vi.fn(),
    listAuthorizationPermissions: vi.fn(),
    listAuthorizationRoles: vi.fn(),
    saveRolePermissions: vi.fn(),
    updateAuthorizationRole: vi.fn(),
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
    archiveApiMocks.listAuthorizationPermissions.mockResolvedValue({ items: [] });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("AuthorizationRolesPage", () => {
    it("does not save empty permissions when permission detail loading fails", async () => {
        archiveApiMocks.getRolePermissions.mockRejectedValue(new Error("网络错误"));

        render(
            <QueryClientProvider client={new QueryClient()}>
                <AuthorizationRolesPage />
            </QueryClientProvider>,
        );

        fireEvent.click(await screen.findByRole("button", { name: /权\s*限/ }));
        await waitFor(() => {
            expect(archiveApiMocks.getRolePermissions).toHaveBeenCalledWith(2);
        });

        const okButton = screen.getByRole("button", { name: "OK" });
        expect(okButton).toHaveAttribute("disabled");
        fireEvent.click(okButton);

        expect(archiveApiMocks.saveRolePermissions).not.toHaveBeenCalled();
    });
});
