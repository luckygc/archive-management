import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { OrganizationDepartmentsPage } from "./OrganizationDepartmentsPage";

const archiveApiMocks = vi.hoisted(() => ({
    createOrganizationDepartment: vi.fn(),
    listOrganizationDepartments: vi.fn(),
    updateOrganizationDepartment: vi.fn(),
}));

vi.mock("@/shared/api/archive", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@/shared/api/archive")>();
    return {
        ...actual,
        ...archiveApiMocks,
    };
});

beforeEach(() => {
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
            {
                id: 2,
                departmentCode: "D002",
                departmentName: "档案部",
                parentId: 1,
                enabled: true,
                sortOrder: 1,
                createdAt: "2026-07-03T00:00:00",
                updatedAt: "2026-07-03T00:00:00",
            },
        ],
    });
    archiveApiMocks.createOrganizationDepartment.mockResolvedValue({
        id: 3,
        departmentCode: "D003",
        departmentName: "法务部",
        enabled: true,
        sortOrder: 0,
        createdAt: "2026-07-03T00:00:00",
        updatedAt: "2026-07-03T00:00:00",
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("OrganizationDepartmentsPage", () => {
    it("renders departments and submits create form", async () => {
        render(
            <QueryClientProvider client={new QueryClient()}>
                <OrganizationDepartmentsPage />
            </QueryClientProvider>,
        );

        expect((await screen.findAllByText("综合部")).length).toBeGreaterThan(0);
        expect(await screen.findByText("档案部")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "新建部门" }));
        fireEvent.change(screen.getByPlaceholderText("例如：D003"), {
            target: { value: "D003" },
        });
        fireEvent.change(screen.getByPlaceholderText("例如：法务部"), {
            target: { value: "法务部" },
        });
        fireEvent.click(screen.getByRole("button", { name: /保\s*存/ }));

        await waitFor(() => {
            expect(archiveApiMocks.createOrganizationDepartment).toHaveBeenCalledWith({
                departmentCode: "D003",
                departmentName: "法务部",
                parentId: undefined,
                enabled: true,
                sortOrder: 0,
            });
        });
    });
});
