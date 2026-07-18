import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import OrganizationDepartmentsPage from "./OrganizationDepartmentsPage.vue";

const archiveApiMocks = vi.hoisted(() => ({
    createOrganizationDepartment: vi.fn(),
    listOrganizationDepartments: vi.fn(),
    updateOrganizationDepartment: vi.fn(),
}));

vi.mock("@/shared/api/organization", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/shared/api/organization")>()),
    ...archiveApiMocks,
}));

beforeEach(() => {
    archiveApiMocks.listOrganizationDepartments.mockResolvedValue({
        items: [
            department({ id: 1, departmentCode: "D001", departmentName: "综合部" }),
            department({
                id: 2,
                departmentCode: "D002",
                departmentName: "档案部",
                parentId: 1,
                sortOrder: 1,
            }),
        ],
    });
    archiveApiMocks.createOrganizationDepartment.mockResolvedValue(
        department({ id: 3, departmentCode: "D003", departmentName: "法务部" }),
    );
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("OrganizationDepartmentsPage", () => {
    it("展示部门树并提交新建部门", async () => {
        render(OrganizationDepartmentsPage, { global: { plugins: [ElementPlus] } });

        expect((await screen.findAllByText("综合部")).length).toBeGreaterThan(0);
        expect(await screen.findByText("档案部")).toBeInTheDocument();

        await fireEvent.click(screen.getByRole("button", { name: "新建部门" }));
        await fireEvent.update(screen.getByPlaceholderText("例如：D003"), "D003");
        await fireEvent.update(screen.getByPlaceholderText("例如：法务部"), "法务部");
        await fireEvent.click(screen.getByRole("button", { name: "保存" }));

        await waitFor(() =>
            expect(archiveApiMocks.createOrganizationDepartment).toHaveBeenCalledWith({
                departmentCode: "D003",
                departmentName: "法务部",
                parentId: undefined,
                enabled: true,
                sortOrder: 0,
            }),
        );
    });
});

function department(values: {
    id: number;
    departmentCode: string;
    departmentName: string;
    parentId?: number;
    sortOrder?: number;
}) {
    return {
        enabled: true,
        sortOrder: 0,
        createdAt: "2026-07-03T00:00:00",
        updatedAt: "2026-07-03T00:00:00",
        ...values,
    };
}
