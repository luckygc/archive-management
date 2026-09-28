import { expect, it, vi } from "vite-plus/test";

import { updateOrganizationDepartment } from "./organization";

const patch = vi.hoisted(() => vi.fn());

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: { patch },
}));

it("通过 JSON Merge Patch 移除部门父级", async () => {
    const payload = { parentId: null };

    await updateOrganizationDepartment(7, payload);

    expect(patch).toHaveBeenCalledWith("/organization-departments/7", payload, {
        headers: { "Content-Type": "application/merge-patch+json" },
    });
});
