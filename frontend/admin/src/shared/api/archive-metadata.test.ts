import { expect, it, vi } from "vite-plus/test";

import { updateArchiveCategory } from "./archive-metadata";

const httpClientMock = vi.hoisted(() => ({ patch: vi.fn() }));

vi.mock("@archive-management/frontend-core/api", () => ({ httpClient: httpClientMock }));

it("分类更新以 Merge Patch 删除空父级", async () => {
    await updateArchiveCategory(12, {
        categoryCode: "contract",
        categoryName: "合同档案",
        managementMode: "ITEM_ONLY",
        enabled: true,
        sortOrder: 0,
    });

    expect(httpClientMock.patch).toHaveBeenCalledWith(
        "/archive-categories/12",
        {
            categoryCode: "contract",
            categoryName: "合同档案",
            managementMode: "ITEM_ONLY",
            parentId: null,
            enabled: true,
            sortOrder: 0,
        },
        { headers: { "Content-Type": "application/merge-patch+json" } },
    );
});
